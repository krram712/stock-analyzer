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
 * To enable a provider, set its API key in Railway environment variables:
 *   GEMINI_API_KEY     — https://aistudio.google.com/apikey  (free, has web search)
 *   PERPLEXITY_API_KEY — https://www.perplexity.ai/settings/api  (free tier, has web search)
 *   GROQ_API_KEY       — https://console.groq.com/keys  (free, very fast, no live data)
 */
@Configuration
public class LlmProviderConfig {

    // ── Perplexity ────────────────────────────────────────────────────────────
    @Value("${perplexity.api.key:}") private String perplexityKey;
    @Value("${perplexity.model:sonar}") private String perplexityModel;

    // ── Groq ──────────────────────────────────────────────────────────────────
    @Value("${groq.api.key:}") private String groqKey;
    @Value("${groq.model:llama-3.3-70b-versatile}") private String groqModel;

    @Bean
    public List<LlmClient> llmProviders(GeminiClient geminiClient, ObjectMapper objectMapper) {
        List<LlmClient> providers = new ArrayList<>();

        // 1. Gemini — primary (Google Search grounding, free 15 RPM)
        providers.add(geminiClient);

        // 2. Perplexity Sonar — has built-in web search, free tier
        if (perplexityKey != null && !perplexityKey.isBlank()) {
            providers.add(new OpenAiCompatibleClient(
                    "Perplexity",
                    "https://api.perplexity.ai",
                    perplexityKey,
                    perplexityModel,
                    8000,
                    120,
                    objectMapper));
        }

        // 3. Groq — very fast, no live web data (knowledge cutoff fallback)
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

