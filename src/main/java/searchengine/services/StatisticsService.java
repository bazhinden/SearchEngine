package searchengine.services;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.repositories.LemmaRepository;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import searchengine.services.interfaces.StatisticsServiceInterface;

@Service
@RequiredArgsConstructor
public class StatisticsService implements StatisticsServiceInterface {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexingService indexingService;

    @Override
    public StatisticsResponse getStatistics() {
        List<SiteEntity> allSites = siteRepository.findAll();

        long totalPagesLong = pageRepository.count();
        long totalLemmasLong = lemmaRepository.count();

        int totalPages = (totalPagesLong > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) totalPagesLong;
        int totalLemmas = (totalLemmasLong > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) totalLemmasLong;

        TotalStatistics totalStatistics = new TotalStatistics();
        totalStatistics.setSites(allSites.size());
        totalStatistics.setPages(totalPages);
        totalStatistics.setLemmas(totalLemmas);

        boolean isIndexing = indexingService.isIndexing();
        totalStatistics.setIndexing(isIndexing);

        List<DetailedStatisticsItem> detailedStatisticsItems = new ArrayList<>();
        for (SiteEntity site : allSites) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setUrl(site.getUrl());
            item.setName(site.getName());
            item.setStatus(site.getStatus().name());

            Date statusTime = site.getStatusTime();
            long epochSeconds = (statusTime != null) ? statusTime.toInstant().getEpochSecond() : 0;
            item.setStatusTime(epochSeconds);

            int pages = (pageRepository.countBySite(site) > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) pageRepository.countBySite(site);
            int lemmas = (lemmaRepository.countBySite(site) > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) lemmaRepository.countBySite(site);

            item.setPages(pages);
            item.setLemmas(lemmas);

            if (site.getLastError() != null) {
                item.setError(site.getLastError());
            }

            detailedStatisticsItems.add(item);
        }

        StatisticsData statisticsData = new StatisticsData();
        statisticsData.setTotal(totalStatistics);
        statisticsData.setDetailed(detailedStatisticsItems);

        StatisticsResponse response = new StatisticsResponse();
        response.setStatistics(statisticsData);
        response.setResult(true);

        return response;
    }
}
