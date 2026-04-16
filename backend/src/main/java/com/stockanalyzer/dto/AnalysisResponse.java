package com.stockanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalysisResponse {

    private boolean success;
    private String message;

    /**
     * Raw JSON string from Claude – passed through without re-serialisation
     * so the frontend receives the exact structure it expects.
     */
    @JsonRawValue
    private String data;

    private long timestamp;
    private long processingTimeMs;
    private String ticker;
    private String horizon;

    /** "LIVE" = freshly fetched from AI; "CACHED" = served from in-memory cache */
    private String dataSource;

    /** Epoch-ms when the data was originally fetched (same as timestamp for LIVE, older for CACHED) */
    private long fetchedAt;

    /** The asOfDate used for this analysis (null = latest available) */
    private String asOfDate;
}
