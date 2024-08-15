package searchengine.dto.indexing;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
@AllArgsConstructor
public class IndexingResponse {
    private boolean result;
    private String error;
    private final HttpStatus status;
}
