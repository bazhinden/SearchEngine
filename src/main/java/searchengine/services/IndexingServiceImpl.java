package searchengine.services;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.config.UserConfig;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.lemmas.LemmaFinder;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.interfaces.IndexingService;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final UserConfig userConfig;
    private final ForkJoinPool forkJoinPool;

    private final LemmaFinder lemmaFinder;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);

    public IndexingServiceImpl(SitesList sitesList, SiteRepository siteRepository, PageRepository pageRepository,
                               UserConfig userConfig, LemmaFinder lemmaFinder, LemmaRepository lemmaRepository,
                               IndexRepository indexRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.userConfig = userConfig;
        this.forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 2);
        this.lemmaFinder = lemmaFinder;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    @Override
    public IndexingResponse startIndexing() {
        if (isIndexing.get()) {
            return new IndexingResponse(false, "Индексация уже запущена", HttpStatus.BAD_REQUEST);
        }
        isIndexing.set(true);
        long startTime = System.currentTimeMillis();
        log.info("Индексация начата: {}", LocalDateTime.now());

        List<Site> sites = sitesList.getSites();
        for (Site siteConfig : sites) {
            SiteEntity site = new SiteEntity();
            site.setUrl(siteConfig.getUrl());
            site.setName(siteConfig.getName());
            site.setStatus(Status.INDEXING);
            site.setStatusTime(new Date());
            siteRepository.save(site);

            WebPageIndexerTask task = new WebPageIndexerTask(site.getUrl(), site, pageRepository, siteRepository, userConfig,
                    lemmaFinder, lemmaRepository, indexRepository);
            forkJoinPool.execute(task);
        }

        forkJoinPool.shutdown();
        try {
            boolean terminated = forkJoinPool.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
            if (!terminated) {
                log.warn("ForkJoinPool не завершил выполнение в отведенное время.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Прерывание ожидания завершения ForkJoinPool", e);
        } finally {
            List<SiteEntity> sitesToUpdate = siteRepository.findByStatus(Status.INDEXING);
            for (SiteEntity site : sitesToUpdate) {
                site.setStatus(Status.INDEXED);
                site.setStatusTime(new Date());
                siteRepository.save(site);
            }
            isIndexing.set(false);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        long minutes = (duration / 1000) / 60;
        long seconds = (duration / 1000) % 60;
        log.info("Индексация завершена: {}", LocalDateTime.now());
        log.info("Время выполнения индексации: {} минут {} секунд", minutes, seconds);

        return new IndexingResponse(true, null, HttpStatus.OK);
    }

    @Override
    public IndexingResponse stopIndexing() {
        if (!isIndexing.get()) {
            return new IndexingResponse(false, "Индексация не запущена", HttpStatus.BAD_REQUEST);
        }

        isIndexing.set(false);
        forkJoinPool.shutdownNow();

        try {
            if (!forkJoinPool.awaitTermination(1, java.util.concurrent.TimeUnit.MINUTES)) {
                log.warn("ForkJoinPool не завершил выполнение за 1 минуту после остановки");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Прерывание ожидания завершения ForkJoinPool", e);
        }

        log.info("Индексация остановлена: {}", LocalDateTime.now());

        List<SiteEntity> sites = siteRepository.findByStatus(Status.INDEXING);
        for (SiteEntity site : sites) {
            site.setStatus(Status.FAILED);
            site.setLastError("Индексация остановлена пользователем");
            site.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
            siteRepository.save(site);
        }

        return new IndexingResponse(true, null,HttpStatus.OK);
    }
    @Override
    public boolean isIndexing() {
        return isIndexing.get();
    }

}
