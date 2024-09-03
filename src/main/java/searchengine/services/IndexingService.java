package searchengine.services;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.config.UserConfig;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.interfaces.IndexingServiceInterface;
import searchengine.util.LemmaFinder;
import searchengine.util.WebPageIndexerTask;

import java.util.Date;
import java.util.List;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class IndexingService implements IndexingServiceInterface {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final UserConfig userConfig;
    private final LemmaFinder lemmaFinder;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    private final ConcurrentMap<String, LemmaEntity> lemmaCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> pageCache = new ConcurrentHashMap<>();

    @Getter
    private final AtomicBoolean isIndexing = new AtomicBoolean(false);

    private final ReentrantLock indexingLock = new ReentrantLock();
    private ForkJoinPool pool;

    public IndexingService(SitesList sitesList, SiteRepository siteRepository, PageRepository pageRepository,
                           UserConfig userConfig, LemmaFinder lemmaFinder, LemmaRepository lemmaRepository,
                           IndexRepository indexRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.userConfig = userConfig;
        this.lemmaFinder = lemmaFinder;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    @Override
    public IndexingResponse startIndexing() {
        if (!indexingLock.tryLock()) {
            return new IndexingResponse(false, "Индексация уже запущена", HttpStatus.BAD_REQUEST);
        }
        try {
            if (isIndexing.get()) {
                return new IndexingResponse(false, "Индексация уже запущена", HttpStatus.BAD_REQUEST);
            }

            log.info("Запуск индексации...");

            isIndexing.set(true);

            if (pool != null) {
                pool.shutdownNow();
                pool = null;
            }
            pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 2);
            log.info("Создание нового пула потоков");

            lemmaCache.clear();
            pageCache.clear();

            List<Site> sites = sitesList.getSites();
            for (Site siteConfig : sites) {
                SiteEntity site = siteRepository.findByUrl(siteConfig.getUrl()).orElse(new SiteEntity());

                log.info("Обработка сайта: {}", siteConfig.getUrl());

                site.setUrl(siteConfig.getUrl());
                site.setName(siteConfig.getName());
                site.setStatus(Status.INDEXING);
                site.setStatusTime(new Date());
                siteRepository.save(site);

                WebPageIndexerTask task = new WebPageIndexerTask(
                        site.getUrl(), site, pageRepository, siteRepository, userConfig,
                        lemmaFinder, lemmaRepository, indexRepository, pageCache, lemmaCache
                );
                log.info("Создание задачи для URL: {}", site.getUrl());
                pool.submit(task);
            }

            return new IndexingResponse(true, null, HttpStatus.OK);
        } finally {
            indexingLock.unlock();
        }
    }

    @Override
    public IndexingResponse stopIndexing() {
        if (!indexingLock.tryLock()) {
            return new IndexingResponse(false, "Индексация не запущена", HttpStatus.BAD_REQUEST);
        }
        try {
            if (!isIndexing.get()) {
                return new IndexingResponse(false, "Индексация не запущена", HttpStatus.BAD_REQUEST);
            }

            log.info("Остановка индексации...");

            isIndexing.set(false);

            if (pool != null) {
                pool.shutdownNow();
                pool = null;
            }

            List<SiteEntity> sites = siteRepository.findByStatus(Status.INDEXING);
            for (SiteEntity site : sites) {
                site.setStatus(Status.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                site.setStatusTime(new Date());
                siteRepository.save(site);
            }

            return new IndexingResponse(true, "Индексация остановлена", HttpStatus.OK);
        } finally {
            indexingLock.unlock();
        }
    }

    @Override
    public boolean isIndexing() {
        return isIndexing.get();
    }
}



