package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.dao.DataIntegrityViolationException;
import searchengine.config.UserConfig;
import searchengine.lemmas.LemmaFinder;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private final Map<String, LemmaEntity> lemmaCache = new ConcurrentHashMap<>();

    public WebPageIndexerTask(String url, SiteEntity site, PageRepository pageRepository, SiteRepository siteRepository,
                              UserConfig userConfig, LemmaFinder lemmaFinder, LemmaRepository lemmaRepository,
                              IndexRepository indexRepository) {
        this.url = url;
        this.site = site;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.userConfig = userConfig;
        this.lemmaFinder = lemmaFinder;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
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

            if (!visitedUrls.add(url)) {
                return null;
            }

            if (!isVisited(url)) {
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
                                    lemmaFinder, lemmaRepository, indexRepository));
                        }
                    }

                    invokeAll(tasks);
                }
            }
        } catch (IOException e) {
            log.error("Error processing URL: {}", url, e);
            saveError(site, e.getMessage());
        } finally {
            addDelay();

        }
        return null;
    }


    private boolean isVisited(String url) {
        return pageRepository.existsByPathAndSite(url.replace(site.getUrl(), ""), site);
    }

    private boolean isValidUrl(String url) {
        return url.startsWith(site.getUrl()) && !url.contains("#") && !url.matches(".*\\.(pdf|jpg|png|zip)$");
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

            Map<String, Integer> lemmas = lemmaFinder.collectLemmas(lemmaFinder.cleanHtml(content));
            updateLemmasAndIndices(lemmas, page);
        } catch (DataIntegrityViolationException e) {
            log.warn("Страница уже существует: {} для сайта: {}", url, site.getUrl());
        }
    }

    private void updateLemmasAndIndices(Map<String, Integer> lemmas, PageEntity page) {
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            String lemmaText = entry.getKey();
            int frequency = entry.getValue();

            LemmaEntity lemma = lemmaCache.computeIfAbsent(lemmaText, key ->
                    lemmaRepository.findByLemmaAndSite(key, page.getSite()).orElse(new LemmaEntity())
            );
            lemma.setLemma(lemmaText);
            lemma.setFrequency(lemma.getFrequency() + 1);
            lemma.setSite(page.getSite());
            lemmaRepository.save(lemma);

            IndexEntity indexEntity = new IndexEntity();
            indexEntity.setPage(page);
            indexEntity.setLemma(lemma);
            indexEntity.setRanking((float) frequency);
            indexRepository.save(indexEntity);
        }
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
}
