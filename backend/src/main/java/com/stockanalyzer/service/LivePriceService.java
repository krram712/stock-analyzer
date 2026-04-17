package com.stockanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Fetches real-time (or most-recent) stock quote from Yahoo Finance.
 * No API key required. Used to ground the LLM prompt with a verified current price.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LivePriceService {

    private static final String YAHOO_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=1d";
    private static final String YAHOO_URL_2 =
            "https://query2.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=1d";

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US);

    private final ObjectMapper objectMapper;

    public record LiveQuote(double price, String priceDate, String marketCap, double high52, double low52) {}

    /**
     * Returns a LiveQuote for the given ticker, or null if the fetch fails.
     */
    public LiveQuote fetchQuote(String ticker) {
        try {
            return doFetch(YAHOO_URL, ticker);
        } catch (Exception e1) {
            log.warn("Yahoo query1 failed for {}: {}. Trying query2...", ticker, e1.getMessage());
            try {
                return doFetch(YAHOO_URL_2, ticker);
            } catch (Exception e2) {
                log.warn("Yahoo query2 also failed for {}: {}. Will proceed without live price.", ticker, e2.getMessage());
                return null;
            }
        }
    }

    private LiveQuote doFetch(String urlTemplate, String ticker) throws Exception {
        WebClient client = WebClient.builder()
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; StockAnalyzer/1.0)")
                .defaultHeader("Accept", "application/json")
                .build();

        String body = client.get()
                .uri(urlTemplate, ticker)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        if (body == null || body.isBlank()) {
            throw new RuntimeException("Empty response from Yahoo Finance");
        }

        JsonNode root = objectMapper.readTree(body);
        JsonNode meta = root.path("chart").path("result").get(0).path("meta");

        double price = meta.path("regularMarketPrice").asDouble(0);
        if (price == 0) price = meta.path("previousClose").asDouble(0);
        if (price == 0) throw new RuntimeException("Price not found in Yahoo Finance response");

        long priceTs = meta.path("regularMarketTime").asLong(0);
        String priceDate = priceTs > 0
                ? Instant.ofEpochSecond(priceTs).atZone(ZoneId.of("America/New_York")).format(DATE_FMT)
                : Instant.now().atZone(ZoneId.of("America/New_York")).format(DATE_FMT);

        double marketCapRaw = meta.path("marketCap").asDouble(0);
        String marketCap = formatMarketCap(marketCapRaw);

        double high52 = meta.path("fiftyTwoWeekHigh").asDouble(0);
        double low52  = meta.path("fiftyTwoWeekLow").asDouble(0);

        log.info("Live quote for {}: price={} date={} mcap={} 52H={} 52L={}",
                ticker, price, priceDate, marketCap, high52, low52);

        return new LiveQuote(price, priceDate, marketCap, high52, low52);
    }

    private static String formatMarketCap(double val) {
        if (val <= 0) return "N/A";
        if (val >= 1_000_000_000_000.0) return String.format("%.2fT", val / 1_000_000_000_000.0);
        if (val >= 1_000_000_000.0)     return String.format("%.2fB", val / 1_000_000_000.0);
        if (val >= 1_000_000.0)         return String.format("%.2fM", val / 1_000_000.0);
        return String.format("%.0f", val);
    }
}

