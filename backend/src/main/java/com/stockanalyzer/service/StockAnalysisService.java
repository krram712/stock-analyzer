package com.stockanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockanalyzer.client.AnthropicClient;
import com.stockanalyzer.dto.AnalysisResponse;
import com.stockanalyzer.exception.AnalysisException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class


StockAnalysisService {

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    private String systemPrompt;

    // Simple in-process rate limiter: last request time per IP
    private final ConcurrentHashMap<String, Long> lastRequestTime = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() throws Exception {
        ClassPathResource resource = new ClassPathResource("system-prompt.txt");
        this.systemPrompt = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        log.info("System prompt loaded ({} chars)", systemPrompt.length());
    }

    /**
     * Run a full fundamental analysis.
     * Results are cached in-memory for 1 hour keyed by ticker+horizon
     * (swap the cache store for Redis in production).
     */
    @Cacheable(value = "stockAnalysis", key = "#ticker.toUpperCase() + '_' + #horizon")
    public AnalysisResponse analyse(String ticker, String horizon, String clientIp) {
        enforceRateLimit(clientIp);

        String upperTicker = ticker.trim().toUpperCase();
        log.info("Starting analysis for {} | horizon={} | ip={}", upperTicker, horizon, clientIp);

        long start = System.currentTimeMillis();

        // Inject horizon into the system prompt template
        String resolvedPrompt = systemPrompt.replace("USER_HORIZON_PLACEHOLDER", horizon);

        // Build user message
        String userMessage = String.format(
                "Analyse the stock with ticker symbol %s for a %s investment horizon. " +
                "Search for the most current financial data available. " +
                "Return ONLY the JSON object as specified in your instructions.",
                upperTicker, horizon
        );

        String rawJson = anthropicClient.complete(resolvedPrompt, userMessage);

        // Validate the response contains expected fields
        validateAnalysisJson(rawJson, upperTicker);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Analysis complete for {} in {}ms", upperTicker, elapsed);

        return AnalysisResponse.builder()
                .success(true)
                .message("Analysis completed successfully")
                .data(rawJson)
                .timestamp(Instant.now().toEpochMilli())
                .processingTimeMs(elapsed)
                .ticker(upperTicker)
                .horizon(horizon)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void validateAnalysisJson(String json, String ticker) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // Check for explicit error from Claude
            if (root.has("error")) {
                String errorMsg = root.path("error").asText();
                log.warn("Claude returned an error for {}: {}", ticker, errorMsg);
                throw new AnalysisException(errorMsg, 404);
            }

            // Ensure minimum structure is present
            if (!root.has("ticker") && !root.has("company")) {
                throw new AnalysisException("Response does not appear to be a valid stock analysis. Please check the ticker symbol.");
            }

        } catch (AnalysisException ae) {
            throw ae;
        } catch (Exception e) {
            throw new AnalysisException("Invalid analysis response structure: " + e.getMessage());
        }
    }

    /**
     * Very simple per-IP rate limiter. In production, use Spring's bucket4j
     * or a Redis-backed rate limiter.
     */
    private void enforceRateLimit(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return;
        Long last = lastRequestTime.get(clientIp);
        if (last != null) {
            long secondsSince = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - last);
            if (secondsSince < 6) { // ≈10 rpm
                throw new AnalysisException(
                        "Too many requests. Please wait a few seconds before trying again.", 429);
            }
        }
        lastRequestTime.put(clientIp, System.currentTimeMillis());
    }
}
