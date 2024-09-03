package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.config.UserConfig;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.util.LemmaFinder;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.interfaces.PageIndexingServiceInterface;

import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
public class PageIndexingService implements PageIndexingServiceInterface {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaFinder lemmaFinder;
    private final UserConfig userConfig;

    public PageIndexingService(SitesList sitesList, SiteRepository siteRepository, PageRepository pageRepository,
                               LemmaRepository lemmaRepository, IndexRepository indexRepository,
                               LemmaFinder lemmaFinder, UserConfig userConfig) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.lemmaFinder = lemmaFinder;
        this.userConfig = userConfig;
    }

    @Override
    public IndexingResponse indexPage(String url) {
        try {
            SiteEntity site = findSiteByUrl(url);
            if (site == null) {
                return new IndexingResponse(false,
                        "Данная страница находится за пределами сайтов, указанных в конфигурационном файле",
                        HttpStatus.BAD_REQUEST);
            }

            PageEntity oldPage = pageRepository.findByPathAndSite(url, site);
            if (oldPage != null) {
                indexRepository.deleteByPage(oldPage);
                pageRepository.delete(oldPage);
            }

            Document doc = Jsoup.connect(url)
                    .userAgent(userConfig.getAgent())
                    .referrer(userConfig.getReferer())
                    .timeout(10000)
                    .get();

            int statusCode = doc.connection().response().statusCode();
            if (statusCode >= 400 && statusCode < 600) {
                return new IndexingResponse(false, "Ошибка HTTP-код: " + statusCode, HttpStatus.valueOf(statusCode));
            }

            String content = doc.outerHtml();
            Map<String, Integer> lemmas = lemmaFinder.collectLemmas(lemmaFinder.cleanHtml(content));

            PageEntity page = new PageEntity();
            page.setPath(url);
            page.setCode(200);
            page.setContent(content);
            page.setSite(site);
            pageRepository.save(page);

            updateLemmasAndIndices(lemmas, page);

            return new IndexingResponse(true, null, HttpStatus.OK);
        } catch (IOException e) {
            log.error("Ошибка при индексации страницы: {}", url, e);
            return new IndexingResponse(false, "Ошибка при скачивании страницы", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private SiteEntity findSiteByUrl(String url) {
        for (Site siteConfig : sitesList.getSites()) {
            if (url.startsWith(siteConfig.getUrl())) {
                return siteRepository.findByUrl(siteConfig.getUrl()).orElse(null);
            }
        }
        return null;
    }

    private void processLemma(Map.Entry<String, Integer> entry, PageEntity page) {
        String lemmaText = entry.getKey();
        int frequency = entry.getValue();

        LemmaEntity lemma = lemmaRepository.findByLemmaAndSite(lemmaText, page.getSite())
                .orElse(new LemmaEntity());
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

    private void updateLemmasAndIndices(Map<String, Integer> lemmas, PageEntity page) {
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            processLemma(entry, page);
        }
    }
}
