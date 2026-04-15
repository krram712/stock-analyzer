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
 * Provider order (configured in LlmProviderConfig):
 *   1. Gemini 2.0 Flash      — Google Search grounding, 15 RPM free
 *   2. Gemini 2.0 Flash Lite — Google Search grounding, 30 RPM free (separate quota)
 *   3. Groq Llama 3.3 70b    — fast, 30 RPM free, no live web data
 *   4. OpenRouter             — free model pool, no live web data
 *   5. Cerebras               — ultra-fast free tier, no live web data
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
        List<String> failed  = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (LlmClient provider : providers) {
            if (!provider.isAvailable()) {
                log.info("Skipping {} — no API key configured in Railway", provider.getProviderName());
                skipped.add(provider.getProviderName());
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
                failed.add(detail);
                // Always try next provider
            }
        }

        // Build a clear error message showing both failed and skipped providers
        StringBuilder msg = new StringBuilder();
        if (!failed.isEmpty()) {
            msg.append("All providers failed:\n").append(String.join("\n", failed));
        }
        if (!skipped.isEmpty()) {
            if (msg.length() > 0) msg.append("\n\n");
            msg.append("Skipped (no API key set in Railway):\n")
               .append(String.join(", ", skipped))
               .append("\n→ Add GROQ_API_KEY / OPENROUTER_API_KEY / CEREBRAS_API_KEY in Railway → Variables");
        }
        if (msg.length() == 0) {
            msg.append("No AI providers configured. Set GEMINI_API_KEY in Railway.");
        }

        String errorSummary = msg.toString();
        log.error("All LLM providers exhausted:\n{}", errorSummary);
        throw new AnalysisException(errorSummary, 503);
    }
}
