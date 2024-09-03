package searchengine.util;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.springframework.dao.DataIntegrityViolationException;
import searchengine.config.UserConfig;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.io.Serial;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RecursiveTask;

@Slf4j
public class WebPageIndexerTask extends RecursiveTask<Void> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String url;
    private final SiteEntity site;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final UserConfig userConfig;
    private final LemmaFinder lemmaFinder;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    private final Set<String> visitedUrls;
    private final ConcurrentMap<String, LemmaEntity> lemmaCache;
    private final ConcurrentMap<String, Boolean> pageCache;

    public WebPageIndexerTask(String url, SiteEntity site, PageRepository pageRepository, SiteRepository siteRepository,
                              UserConfig userConfig, LemmaFinder lemmaFinder, LemmaRepository lemmaRepository,
                              IndexRepository indexRepository, ConcurrentMap<String, Boolean> pageCache,
                              ConcurrentMap<String, LemmaEntity> lemmaCache) {
        this.url = url;
        this.site = site;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.userConfig = userConfig;
        this.lemmaFinder = lemmaFinder;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.visitedUrls = ConcurrentHashMap.newKeySet();
        this.pageCache = pageCache;
        this.lemmaCache = lemmaCache;
    }

    @Override
    protected Void compute() {
        try {
            if (Thread.currentThread().isInterrupted()) {
                log.info("Индексация прервана для URL: {}", url);
                site.setStatus(Status.FAILED);
                site.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
                siteRepository.save(site);
                return null;
            }

            if (!visitedUrls.add(url) || pageCache.containsKey(url)) {
                return null;
            }

            Connection.Response response = Jsoup.connect(url)
                    .userAgent(userConfig.getAgent())
                    .referrer(userConfig.getReferer())
                    .timeout(10000)
                    .execute();

            if (isValidStatusCode(response.statusCode())) {
                Document doc = response.parse();
                savePage(url, response.statusCode(), doc.outerHtml());
                Elements links = doc.select("a[href]");
                Set<WebPageIndexerTask> tasks = new HashSet<>();

                for (Element link : links) {
                    String childUrl = link.absUrl("href");
                    if (isValidUrl(childUrl) && !visitedUrls.contains(childUrl)) {
                        tasks.add(new WebPageIndexerTask(childUrl, site, pageRepository, siteRepository, userConfig,
                                lemmaFinder, lemmaRepository, indexRepository, pageCache, lemmaCache));
                    }
                }

                invokeAll(tasks);
            }
        } catch (IOException e) {
            log.error("Error processing URL: {}", url, e);
            saveError(site, e.getMessage());
        } finally {
            addDelay();
        }
        return null;
    }

    private void savePage(String url, int statusCode, String content) {
        if (!isValidStatusCode(statusCode)) {
            return;
        }

        try {
            PageEntity page = new PageEntity();
            page.setPath(url.replace(site.getUrl(), ""));
            page.setCode(statusCode);
            page.setContent(content);
            page.setSite(site);
            page.setStatus(Status.INDEXED);

            pageRepository.save(page);
            pageCache.put(url, true);

            Map<String, Integer> lemmas = lemmaFinder.collectLemmas(lemmaFinder.cleanHtml(content));
            updateLemmasAndIndices(lemmas, page);
        } catch (DataIntegrityViolationException e) {
            log.warn("Страница уже существует: {} для сайта: {}", url, site.getUrl());
        }
    }

    private void updateLemmasAndIndices(Map<String, Integer> lemmas, PageEntity page) {
        Map<LemmaEntity, Integer> lemmaFrequencyMap = new HashMap<>();

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            String lemmaText = entry.getKey();
            int frequency = entry.getValue();

            LemmaEntity lemma = lemmaCache.computeIfAbsent(lemmaText, key ->
                    lemmaRepository.findByLemmaAndSite(key, page.getSite()).orElse(new LemmaEntity())
            );
            lemma.setLemma(lemmaText);
            lemma.setFrequency(lemma.getFrequency() + frequency);
            lemma.setSite(page.getSite());
            lemmaFrequencyMap.put(lemma, lemmaFrequencyMap.getOrDefault(lemma, 0) + frequency);
        }

        lemmaRepository.saveAll(lemmaCache.values());

        List<IndexEntity> indexEntities = new ArrayList<>();
        for (Map.Entry<LemmaEntity, Integer> entry : lemmaFrequencyMap.entrySet()) {
            LemmaEntity lemma = entry.getKey();
            int frequency = entry.getValue();

            IndexEntity indexEntity = new IndexEntity();
            indexEntity.setPage(page);
            indexEntity.setLemma(lemma);
            indexEntity.setRanking((float) frequency);
            indexEntities.add(indexEntity);
        }
        indexRepository.saveAll(indexEntities);
    }

    private void saveError(SiteEntity site, String error) {
        site.setStatus(Status.FAILED);
        site.setLastError(error);
        site.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
        siteRepository.save(site);
    }

    private void addDelay() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isValidStatusCode(int statusCode) {
        if (statusCode >= 400 && statusCode < 600) {
            log.warn("Не индексируем страницу с ошибочным HTTP-кодом {}: {}", statusCode, url);
            return false;
        }
        return true;
    }

    private boolean isValidUrl(String url) {
        return url.startsWith(site.getUrl()) && !url.contains("#") && !url.matches(".*\\.(pdf|jpg|png|zip)$");
    }
}


