package com.stockanalyzer.controller;

import com.stockanalyzer.dto.AnalysisRequest;
import com.stockanalyzer.dto.AnalysisResponse;
import com.stockanalyzer.service.StockAnalysisService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StockAnalysisController {

    private final StockAnalysisService stockAnalysisService;

    /**
     * POST /api/v1/analyze
     *
     * Request body:
     * {
     *   "ticker": "NVDA",
     *   "horizon": "5 years"
     * }
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyze(
            @Valid @RequestBody AnalysisRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        log.info("Received analysis request: ticker={}, horizon={}, ip={}",
                request.getTicker(), request.getHorizon(), clientIp);

        AnalysisResponse response = stockAnalysisService.analyse(
                request.getTicker(),
                request.getHorizon(),
                clientIp,
                request.getAsOfDate()
        );

        return ResponseEntity.ok()
                .header("X-Processing-Time-Ms", String.valueOf(response.getProcessingTimeMs()))
                .header("X-Data-Source", response.getDataSource())
                .header("X-Fetched-At", String.valueOf(response.getFetchedAt()))
                .body(response);
    }

    /**
     * GET /api/v1/health
     * Simple health check (also available via Actuator at /actuator/health)
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "stock-analyzer-backend",
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * GET /api/v1/tickers/popular
     * Returns a curated list of popular tickers for the search UI.
     */
    @GetMapping("/tickers/popular")
    public ResponseEntity<Map<String, Object>> popularTickers() {
        return ResponseEntity.ok(Map.of(
                "tickers", new String[]{
                        "AAPL", "MSFT", "NVDA", "GOOGL", "AMZN",
                        "META", "TSLA", "JPM", "JNJ", "BRK.B",
                        "V", "UNH", "XOM", "AVGO", "LLY"
                }
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp;
        return request.getRemoteAddr();
    }
}
