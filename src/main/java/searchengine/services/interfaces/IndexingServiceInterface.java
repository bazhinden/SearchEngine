package searchengine.services.interfaces;

import searchengine.dto.indexing.IndexingResponse;

public interface IndexingServiceInterface {
    IndexingResponse startIndexing();
    IndexingResponse stopIndexing();
    boolean isIndexing();
}
