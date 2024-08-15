package searchengine.services.interfaces;

import searchengine.dto.indexing.IndexingResponse;

public interface PageIndexingService {
    IndexingResponse indexPage(String url);
}
