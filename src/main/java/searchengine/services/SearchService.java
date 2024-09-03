package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.search.SearchResult;
import searchengine.dto.search.SearchResponse;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.services.interfaces.SearchServiceInterface;
import searchengine.util.CacheManagement;
import searchengine.util.LemmaFinder;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService implements SearchServiceInterface {

    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final LemmaFinder lemmaFinder;
    private final SitesList sitesList;
    private final CacheManagement cacheManagement;

    private Map<PageEntity, List<IndexEntity>> indicesByPage;

    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        validateSearchParameters(query);

        List<SearchResult> allResults = new ArrayList<>();
        String cacheKey = generateCacheKey(query, site);

        if (site == null || site.isEmpty()) {
            List<SearchResult> cachedResults = cacheManagement.getAllSitesSearchResultsFromCache(cacheKey);
            if (cachedResults != null) {
                allResults.addAll(cachedResults);
            } else {
                for (searchengine.config.Site currentSite : sitesList.getSites()) {
                    List<SearchResult> siteResults = performSearch(query, currentSite.getUrl());
                    allResults.addAll(siteResults);
                }
                cacheManagement.putAllSitesSearchResultsInCache(cacheKey, allResults);
            }
        } else {
            List<SearchResult> cachedResults = cacheManagement.getSingleSiteSearchResultsFromCache(cacheKey);
            if (cachedResults != null) {
                allResults.addAll(cachedResults);
            } else {
                List<SearchResult> siteResults = performSearch(query, site);
                allResults.addAll(siteResults);
                cacheManagement.putSingleSiteSearchResultsInCache(cacheKey, allResults);
            }
        }

        int totalResults = allResults.size();

        List<SearchResult> paginatedResults = allResults.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());

        log.info("Поиск завершен по запросу: '{}', site: '{}', offset: {}, limit: {}", query, site, offset, limit);
        return new SearchResponse(true, totalResults, paginatedResults);
    }

    private void validateSearchParameters(String query) {
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("Пустой поисковый запрос");
        }
    }

    private String generateCacheKey(String query, String site) {
        if (site == null || site.isEmpty()) {
            return "all_sites_" + query;
        } else {
            return site + "_" + query;
        }
    }

    public List<SearchResult> performSearch(String query, String site) {
        Set<String> lemmas = lemmaFinder.getLemmaSet(query);
        List<String> sortedLemmas = sortLemmasByFrequency(lemmas);

        List<PageEntity> pages = findPagesByLemma(lemmas, site);
        if (pages.isEmpty()) {
            return Collections.emptyList();
        }

        List<LemmaEntity> lemmaEntities = lemmas.stream()
                .map(lemma -> cacheManagement.getLemmaFromCache(lemma))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (lemmaEntities.isEmpty()) {
            lemmaEntities = lemmaRepository.findByLemmaIn(new ArrayList<>(lemmas));
            lemmaEntities.forEach(lemma -> cacheManagement.putLemmaInCache(lemma.getLemma(), lemma));
        }

        List<IndexEntity> allIndices = indexRepository.findByPagesAndLemmas(pages, sortedLemmas);
        this.indicesByPage = allIndices.stream()
                .collect(Collectors.groupingBy(IndexEntity::getPage));

        double maxRelevance = 0.0;
        List<CompletableFuture<SearchResult>> futureResults = new ArrayList<>();

        for (PageEntity page : pages) {
            double relevance = calculateRelevance(page, lemmaEntities);
            if (relevance > maxRelevance) {
                maxRelevance = relevance;
            }
            futureResults.add(createSearchResultAsync(page, relevance, sortedLemmas));
        }

        List<SearchResult> searchResults = futureResults.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        if (maxRelevance > 0.0) {
            for (SearchResult result : searchResults) {
                result.setRelevance(result.getRelevance() / maxRelevance);
            }
        }

        searchResults.sort(Comparator.comparingDouble(SearchResult::getRelevance).reversed());

        return searchResults;
    }

    private List<String> sortLemmasByFrequency(Set<String> lemmas) {
        Map<String, Integer> lemmaFrequencyMap = new HashMap<>();
        for (String lemma : lemmas) {
            int frequency = lemmaRepository.countByLemma(lemma);
            lemmaFrequencyMap.put(lemma, frequency);
        }

        return lemmaFrequencyMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<PageEntity> findPagesByLemma(Set<String> lemmas, String site) {
        return pageRepository.findPagesByLemmasAndSite(new ArrayList<>(lemmas), site, lemmas.size());
    }

    private double calculateRelevance(PageEntity page, List<LemmaEntity> lemmaEntities) {
        List<IndexEntity> indices = this.indicesByPage.get(page);

        if (indices == null || indices.isEmpty()) {
            return 0.0;
        }

        Set<Long> lemmaIds = lemmaEntities.stream()
                .map(LemmaEntity::getId)
                .collect(Collectors.toSet());

        return indices.stream()
                .filter(index -> lemmaIds.contains(index.getLemma().getId()))
                .mapToDouble(IndexEntity::getRanking)
                .sum();
    }

    private CompletableFuture<SearchResult> createSearchResultAsync(PageEntity page, double relevance, List<String> sortedLemmas) {
        return CompletableFuture.supplyAsync(() -> createSearchResult(page, relevance, sortedLemmas));
    }

    private SearchResult createSearchResult(PageEntity page, double relevance, List<String> sortedLemmas) {
        SearchResult result = new SearchResult();
        result.setSite(page.getSite().getUrl());
        result.setSiteName(page.getSite().getName());
        result.setUri(page.getPath());
        result.setTitle(extractTitle(page.getContent()));
        result.setSnippet(createSnippet(page.getContent(), sortedLemmas));
        result.setRelevance(relevance);
        return result;
    }

    private String extractTitle(String content) {
        Document document = Jsoup.parse(content);
        return document.title();
    }

    private String createSnippet(String content, List<String> sortedLemmas) {
        String cleanContent = Jsoup.parse(content).text();
        String bestSnippet = "";
        for (String lemma : sortedLemmas) {
            int keywordIndex = cleanContent.indexOf(lemma);
            if (keywordIndex != -1) {
                int snippetStart = Math.max(0, keywordIndex - 150);
                int snippetEnd = Math.min(cleanContent.length(), keywordIndex + lemma.length() + 150);
                bestSnippet = cleanContent.substring(snippetStart, snippetEnd);
                bestSnippet = highlightKeywords(bestSnippet, sortedLemmas);
                return bestSnippet;
            }
        }
        if (cleanContent.length() > 300) {
            bestSnippet = cleanContent.substring(0, 300);
        }
        bestSnippet = highlightKeywords(bestSnippet, sortedLemmas);
        return bestSnippet;
    }

    private String highlightKeywords(String snippet, List<String> sortedLemmas) {
        StringBuilder snippetBuilder = new StringBuilder();
        String[] words = snippet.split("\\s+");
        boolean previousWordWasHighlighted = false;

        for (String word : words) {
            List<String> lemmas = lemmaFinder.getLemmaSet(word).stream().toList();
            String lemma = lemmas.isEmpty() ? word : lemmas.get(0);
            boolean highlightWord = sortedLemmas.contains(lemma);

            if (highlightWord) {
                if (!previousWordWasHighlighted) {
                    snippetBuilder.append("<b>");
                }
                snippetBuilder.append(word).append(" ");
                previousWordWasHighlighted = true;
            } else {
                if (previousWordWasHighlighted) {
                    snippetBuilder.append("</b>");
                }
                snippetBuilder.append(word).append(" ");
                previousWordWasHighlighted = false;
            }
        }
        if (previousWordWasHighlighted) {
            snippetBuilder.append("</b>");
        }
        return snippetBuilder.toString().trim();
    }
}