package searchengine.dto.search;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@JsonPropertyOrder({ "result", "count", "data" })
public class SearchResponse {
    private boolean result;
    private int count;
    private List<SearchResult> data;
}