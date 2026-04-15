package com.stockanalyzer.client;

import com.stockanalyzer.exception.AnalysisException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Tries each configured LLM provider in order.
 * On rate-limit (429) or unavailable (503) it moves to the next provider.
 * Any other error (400, 401, 500) is re-thrown immediately.
 *
 * Provider order (configured in LlmProviderConfig):
 *   1. Gemini 1.5 Flash   — Google Search grounding, 15 RPM free
 *   2. Perplexity Sonar   — built-in web search, free tier
 *   3. Groq Llama 3.3 70b — no web search but very fast; knowledge cutoff
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
        List<String> errors = new ArrayList<>();

        for (LlmClient provider : providers) {
            if (!provider.isAvailable()) {
                log.debug("Skipping {} — not configured (no API key)", provider.getProviderName());
                continue;
            }
            try {
                log.info("Attempting analysis with provider: {}", provider.getProviderName());
                String result = provider.complete(systemPrompt, userMessage);
                log.info("Analysis succeeded via {}", provider.getProviderName());
                return result;
            } catch (AnalysisException ex) {
                String detail = provider.getProviderName() + " → " + ex.getMessage() + " (status=" + ex.getStatusCode() + ")";
                log.warn("Provider failed: {}", detail);
                errors.add(detail);
                // Always try next provider — don't stop on any single failure
            }
        }

        String errorSummary = errors.isEmpty()
                ? "No AI providers are configured. Set GEMINI_API_KEY in Railway."
                : "All providers failed:\n" + String.join("\n", errors);

        log.error("All LLM providers exhausted. Details:\n{}", errorSummary);
        throw new AnalysisException(errorSummary, 503);
    }
}

