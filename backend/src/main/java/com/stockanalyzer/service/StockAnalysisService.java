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
    private final LivePriceService livePriceService;

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
     * Caching is disabled — every request always fetches fresh live data.
     * dataSource is always "LIVE".
     */
    public AnalysisResponse analyse(String ticker, String horizon, String clientIp, String asOfDate) {
        enforceRateLimit(clientIp);

        String upperTicker = ticker.trim().toUpperCase();
        String normalizedDate = (asOfDate != null && !asOfDate.isBlank()) ? asOfDate.trim() : null;

        log.info("Fetching LIVE data for {} | horizon={} | asOfDate={} | ip={}", upperTicker, horizon, normalizedDate, clientIp);
        long start = System.currentTimeMillis();

        // ── Fetch live price from Yahoo Finance ───────────────────────────────
        LivePriceService.LiveQuote liveQuote = (normalizedDate == null) ? livePriceService.fetchQuote(upperTicker) : null;

        // Inject horizon into the system prompt template
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

        // ── Inject verified live market data so LLM can't use stale training data ──
        String liveDataInstruction = "";
        if (liveQuote != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                "IMPORTANT – VERIFIED LIVE MARKET DATA (from Yahoo Finance, fetched right now): " +
                "current price = $%.2f, priceDate = \"%s\"",
                liveQuote.price(), liveQuote.priceDate()));
            if (liveQuote.marketCap() != null && !liveQuote.marketCap().equals("N/A"))
                sb.append(String.format(", marketCap = \"%s\"", liveQuote.marketCap()));
            if (liveQuote.high52() > 0)
                sb.append(String.format(", 52-week high = $%.2f", liveQuote.high52()));
            if (liveQuote.low52() > 0)
                sb.append(String.format(", 52-week low = $%.2f", liveQuote.low52()));
            sb.append(". You MUST use exactly these values in the JSON 'price', 'priceDate', 'marketCap', 'high52', and 'low52' fields. Do NOT substitute training-data or cached prices.");
            liveDataInstruction = sb.toString();
            log.info("Injecting live quote into prompt for {}: price={}", upperTicker, liveQuote.price());
        }

        String userMessage = String.format(
                "Analyse the stock with ticker symbol %s for a %s investment horizon. " +
                "%s %s " +
                "Return ONLY the JSON object as specified in your instructions.",
                upperTicker, horizon, dateInstruction, liveDataInstruction
        );

        String rawJson = llmClient.complete(resolvedPrompt, userMessage);
        validateAnalysisJson(rawJson, upperTicker);

        long elapsed = System.currentTimeMillis() - start;
        long fetchedAt = Instant.now().toEpochMilli();
        log.info("Analysis complete for {} in {}ms", upperTicker, elapsed);

        return AnalysisResponse.builder()
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
