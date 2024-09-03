package searchengine.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import searchengine.dto.search.SearchResult;
import searchengine.model.LemmaEntity;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Getter
@Slf4j
@Component
public class CacheManagement {

    public final Cache<String, Boolean> pageUrlCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    protected final Cache<String, LemmaEntity> lemmaCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();


    protected final Cache<String, List<SearchResult>> allSitesSearchResultsCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();


    protected final Cache<String, List<SearchResult>> singleSiteSearchResultsCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    /*  public void clearCache() {
        pageUrlCache.invalidateAll();
        lemmaCache.invalidateAll();
        allSitesSearchResultsCache.invalidateAll();
        singleSiteSearchResultsCache.invalidateAll();
        log.info("Кэши очищены.");
    }*/

    public LemmaEntity getLemmaFromCache(String lemma) {
        return lemmaCache.getIfPresent(lemma);
    }

    public void putLemmaInCache(String lemma, LemmaEntity lemmaEntity) {
        lemmaCache.put(lemma, lemmaEntity);
    }

    public List<SearchResult> getAllSitesSearchResultsFromCache(String key) {
        return allSitesSearchResultsCache.getIfPresent(key);
    }

    public void putAllSitesSearchResultsInCache(String key, List<SearchResult> searchResults) {
        allSitesSearchResultsCache.put(key, searchResults);
    }

    public List<SearchResult> getSingleSiteSearchResultsFromCache(String key) {
        return singleSiteSearchResultsCache.getIfPresent(key);
    }

    public void putSingleSiteSearchResultsInCache(String key, List<SearchResult> searchResults) {
        singleSiteSearchResultsCache.put(key, searchResults);
    }
}