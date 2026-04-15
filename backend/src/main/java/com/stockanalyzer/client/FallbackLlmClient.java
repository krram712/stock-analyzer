package com.stockanalyzer.client;

import com.stockanalyzer.exception.AnalysisException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
        AnalysisException lastError = null;

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
                int code = ex.getStatusCode();
                if (code == 429 || code == 503) {
                    log.warn("Provider {} unavailable ({}): {}. Trying next provider...",
                            provider.getProviderName(), code, ex.getMessage());
                    lastError = ex;
                } else {
                    // Hard error (auth, bad request, etc.) — don't try next provider
                    throw ex;
                }
            }
        }

        throw new AnalysisException(
                lastError != null
                        ? "All AI providers are rate-limited or unavailable. Please wait 60s and try again."
                        : "No AI providers are configured. Set at least GEMINI_API_KEY in Railway environment variables.",
                429);
    }
}

