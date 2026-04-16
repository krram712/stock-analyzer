package com.stockanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockanalyzer.client.FallbackLlmClient;
import com.stockanalyzer.dto.AnalysisResponse;
import com.stockanalyzer.exception.AnalysisException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockAnalysisService {

    private final FallbackLlmClient llmClient;
    private final ObjectMapper objectMapper;

    private String systemPrompt;

    // Simple in-process rate limiter: last request time per IP
    private final ConcurrentHashMap<String, Long> lastRequestTime = new ConcurrentHashMap<>();

    // Manual 1-hour cache: key → cached response
    private final ConcurrentHashMap<String, AnalysisResponse> analysisCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = TimeUnit.HOURS.toMillis(1);

    @PostConstruct
    public void init() throws Exception {
        ClassPathResource resource = new ClassPathResource("system-prompt.txt");
        this.systemPrompt = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        log.info("System prompt loaded ({} chars)", systemPrompt.length());
    }

    /**
     * Run a full fundamental analysis.
     * Results are cached in-memory for 1 hour keyed by ticker+horizon+asOfDate.
     * Returns dataSource="LIVE" for fresh results, "CACHED" for cache hits.
     */
    public AnalysisResponse analyse(String ticker, String horizon, String clientIp, String asOfDate) {
        enforceRateLimit(clientIp);

        String upperTicker = ticker.trim().toUpperCase();
        String normalizedDate = (asOfDate != null && !asOfDate.isBlank()) ? asOfDate.trim() : null;
        String cacheKey = upperTicker + "_" + horizon + (normalizedDate != null ? "_" + normalizedDate : "");

        // Check cache
        AnalysisResponse cached = analysisCache.get(cacheKey);
        if (cached != null) {
            long ageMs = System.currentTimeMillis() - cached.getFetchedAt();
            if (ageMs < CACHE_TTL_MS) {
                log.info("Cache HIT for {} | age={}s", cacheKey, ageMs / 1000);
                // Return with CACHED marker and current request timestamp
                return AnalysisResponse.builder()
                        .success(cached.isSuccess())
                        .message(cached.getMessage())
                        .data(cached.getData())
                        .timestamp(Instant.now().toEpochMilli())
                        .processingTimeMs(cached.getProcessingTimeMs())
                        .ticker(cached.getTicker())
                        .horizon(cached.getHorizon())
                        .dataSource("CACHED")
                        .fetchedAt(cached.getFetchedAt())
                        .asOfDate(normalizedDate)
                        .build();
            } else {
                analysisCache.remove(cacheKey);
            }
        }

        log.info("Cache MISS for {} | horizon={} | asOfDate={} | ip={}", upperTicker, horizon, normalizedDate, clientIp);
        long start = System.currentTimeMillis();

        // Inject horizon and asOfDate into the system prompt template
        String resolvedPrompt = systemPrompt.replace("USER_HORIZON_PLACEHOLDER", horizon);

        // Build user message
        String dateInstruction;
        if (normalizedDate != null) {
            dateInstruction = String.format(
                "Search for financial data as of %s (or the closest available date before it). " +
                "In the 'priceDate' and 'lastUpdated' fields, report the actual date the data is from.",
                normalizedDate);
        } else {
            dateInstruction = "Search for the most current (latest available) financial data. " +
                "In the 'priceDate' and 'lastUpdated' fields, report today's date or the most recent trading date.";
        }

        String userMessage = String.format(
                "Analyse the stock with ticker symbol %s for a %s investment horizon. " +
                "%s " +
                "Return ONLY the JSON object as specified in your instructions.",
                upperTicker, horizon, dateInstruction
        );

        String rawJson = llmClient.complete(resolvedPrompt, userMessage);
        validateAnalysisJson(rawJson, upperTicker);

        long elapsed = System.currentTimeMillis() - start;
        long fetchedAt = Instant.now().toEpochMilli();
        log.info("Analysis complete for {} in {}ms", upperTicker, elapsed);

        AnalysisResponse response = AnalysisResponse.builder()
                .success(true)
                .message("Analysis completed successfully")
                .data(rawJson)
                .timestamp(fetchedAt)
                .processingTimeMs(elapsed)
                .ticker(upperTicker)
                .horizon(horizon)
                .dataSource("LIVE")
                .fetchedAt(fetchedAt)
                .asOfDate(normalizedDate)
                .build();

        analysisCache.put(cacheKey, response);
        return response;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void validateAnalysisJson(String json, String ticker) {
        try {
            JsonNode root = objectMapper.readTree(json);

            if (root.has("error")) {
                String errorMsg = root.path("error").asText();
                log.warn("LLM returned an error for {}: {}", ticker, errorMsg);
                throw new AnalysisException(errorMsg, 404);
            }

            if (!root.has("ticker") && !root.has("company")) {
                throw new AnalysisException("Response does not appear to be a valid stock analysis. Please check the ticker symbol.");
            }

        } catch (AnalysisException ae) {
            throw ae;
        } catch (Exception e) {
            throw new AnalysisException("Invalid analysis response structure: " + e.getMessage());
        }
    }

    private void enforceRateLimit(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return;
        Long last = lastRequestTime.get(clientIp);
        if (last != null) {
            long secondsSince = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - last);
            if (secondsSince < 6) {
                throw new AnalysisException(
                        "Too many requests. Please wait a few seconds before trying again.", 429);
            }
        }
        lastRequestTime.put(clientIp, System.currentTimeMillis());
    }
}
