package searchengine.services.interfaces;

import searchengine.dto.search.SearchResponse;

public interface SearchServiceInterface {
    SearchResponse search(String query, String site, int offset, int limit);
}
