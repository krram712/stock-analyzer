package com.stockanalyzer.client;

import com.stockanalyzer.exception.AnalysisException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Tries each configured LLM provider in order.
 * Falls through to the next provider on any error.
 *
 * Special handling:
 *  - 429 (rate-limited): tries next provider immediately
 *  - If EVERY provider returns 429, waits 65s then retries all once more
 *  - Non-429 errors: tries next provider
 */
@Slf4j
@Component
public class FallbackLlmClient {

    private final List<LlmClient> providers;

    public FallbackLlmClient(List<LlmClient> providers) {
        this.providers = providers;
        providers.forEach(p -> log.info("LLM provider registered: {} (available={})",
                p.getProviderName(), p.isAvailable()));
    }

    public String complete(String systemPrompt, String userMessage) {
        // First pass — try all providers
        List<String> failed  = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int rateLimitedCount = 0;
        int availableCount   = 0;

        for (LlmClient provider : providers) {
            if (!provider.isAvailable()) {
                skipped.add(provider.getProviderName());
                continue;
            }
            availableCount++;
            try {
                log.info("Attempting analysis with provider: {}", provider.getProviderName());
                String result = provider.complete(systemPrompt, userMessage);
                log.info("Analysis succeeded via {}", provider.getProviderName());
                return result;
            } catch (AnalysisException ex) {
                String detail = provider.getProviderName() + " → " + ex.getMessage()
                        + " (status=" + ex.getStatusCode() + ")";
                log.warn("Provider failed: {}", detail);
                failed.add(detail);
                if (ex.getStatusCode() == 429) rateLimitedCount++;
            }
        }

        // If majority of available providers were rate-limited (≥60%), wait 65s and retry
        boolean mostlyRateLimited = availableCount > 0
                && (rateLimitedCount * 100 / availableCount) >= 60;

        if (mostlyRateLimited) {
            log.warn("Most providers rate-limited ({}/{}). Waiting 65s before retry...",
                    rateLimitedCount, availableCount);
            try { Thread.sleep(65_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

            failed.clear();
            for (LlmClient provider : providers) {
                if (!provider.isAvailable()) continue;
                try {
                    log.info("Retry attempt with provider: {}", provider.getProviderName());
                    String result = provider.complete(systemPrompt, userMessage);
                    log.info("Retry succeeded via {}", provider.getProviderName());
                    return result;
                } catch (AnalysisException ex) {
                    String detail = provider.getProviderName() + " → " + ex.getMessage()
                            + " (status=" + ex.getStatusCode() + ")";
                    log.warn("Retry provider failed: {}", detail);
                    failed.add(detail);
                }
            }
        }

        // Build error message
        StringBuilder msg = new StringBuilder();
        if (!failed.isEmpty()) {
            msg.append("All providers failed: ").append(String.join(" | ", failed));
        }
        if (!skipped.isEmpty()) {
            if (msg.length() > 0) msg.append("\n\n");
            msg.append("Skipped (no API key): ").append(String.join(", ", skipped))
               .append("\n→ Add GROQ_API_KEY / OPENROUTER_API_KEY / CEREBRAS_API_KEY / SAMBANOVA_API_KEY / TOGETHER_API_KEY in Railway → Variables");
        }
        if (msg.length() == 0) {
            msg.append("No AI providers configured. Set GEMINI_API_KEY in Railway.");
        }

        String errorSummary = msg.toString();
        log.error("All LLM providers exhausted:\n{}", errorSummary);
        throw new AnalysisException(errorSummary, 503);
    }
}
