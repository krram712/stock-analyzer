package com.stockanalyzer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockanalyzer.client.GeminiClient;
import com.stockanalyzer.client.LlmClient;
import com.stockanalyzer.client.OpenAiCompatibleClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers all LLM providers in priority order for FallbackLlmClient.
 *
 * FREE providers — set their API keys in Railway environment variables:
 *   GEMINI_API_KEY  — https://aistudio.google.com/apikey  (free, 15 RPM, has web search)
 *   GROQ_API_KEY    — https://console.groq.com/keys        (free, 30 RPM, very fast)
 *
 * NOTE: Perplexity API requires a paid account — NOT included by default.
 */
@Configuration
public class LlmProviderConfig {

    // ── Groq (free — https://console.groq.com/keys) ───────────────────────────
    @Value("${groq.api.key:}") private String groqKey;
    @Value("${groq.model:llama-3.3-70b-versatile}") private String groqModel;

    @Bean
    public List<LlmClient> llmProviders(GeminiClient geminiClient, ObjectMapper objectMapper) {
        List<LlmClient> providers = new ArrayList<>();

        // 1. Gemini 1.5 Flash — primary (Google Search grounding, 15 RPM free)
        providers.add(geminiClient);

        // 2. Groq Llama 3.3 70b — fallback (30 RPM free, very fast, no live web data)
        if (groqKey != null && !groqKey.isBlank()) {
            providers.add(new OpenAiCompatibleClient(
                    "Groq",
                    "https://api.groq.com/openai",
                    groqKey,
                    groqModel,
                    8000,
                    60,
                    objectMapper));
        }

        return providers;
    }
}

