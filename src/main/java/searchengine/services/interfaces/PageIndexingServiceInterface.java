package searchengine.services.interfaces;

import searchengine.dto.indexing.IndexingResponse;

public interface PageIndexingServiceInterface {
    IndexingResponse indexPage(String url);
}
