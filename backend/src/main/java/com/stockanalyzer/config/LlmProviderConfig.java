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
 * FREE providers — add API keys in Railway → Variables:
 *
 *  GEMINI_API_KEY      https://aistudio.google.com/apikey       15 RPM  web-search ✓
 *  GROQ_API_KEY        https://console.groq.com/keys            30 RPM  fast       ✓
 *  OPENROUTER_API_KEY  https://openrouter.ai/keys               free models pool   ✓
 *  CEREBRAS_API_KEY    https://cloud.cerebras.ai                 ultra-fast        ✓
 */
@Configuration
public class LlmProviderConfig {

    @Value("${groq.api.key:}")          private String groqKey;
    @Value("${groq.model:llama-3.3-70b-versatile}") private String groqModel;

    @Value("${openrouter.api.key:}")    private String openrouterKey;
    @Value("${openrouter.model:meta-llama/llama-3.3-70b-instruct:free}") private String openrouterModel;

    @Value("${cerebras.api.key:}")      private String cerebrasKey;
    @Value("${cerebras.model:llama-3.3-70b}") private String cerebrasModel;

    @Bean
    public List<LlmClient> llmProviders(GeminiClient geminiClient, ObjectMapper objectMapper) {
        List<LlmClient> providers = new ArrayList<>();

        // 1. Gemini 1.5 Flash — primary (Google Search grounding, 15 RPM free)
        providers.add(geminiClient);

        // 2. Groq — very fast, free 30 RPM (no live web data)
        // Always added — isAvailable() returns false when key is blank, FallbackLlmClient skips it
        providers.add(new OpenAiCompatibleClient(
                "Groq", "https://api.groq.com/openai",
                groqKey, groqModel, 8000, 60, objectMapper));

        // 3. OpenRouter — routes to many free models (no live web data)
        providers.add(new OpenAiCompatibleClient(
                "OpenRouter", "https://openrouter.ai/api",
                openrouterKey, openrouterModel, 8000, 120, objectMapper,
                java.util.Map.of("HTTP-Referer", "https://stock-analyzer-neon.vercel.app",
                                 "X-Title", "Stock Analyser")));

        // 4. Cerebras — ultra-fast inference, free tier (no live web data)
        providers.add(new OpenAiCompatibleClient(
                "Cerebras", "https://api.cerebras.ai",
                cerebrasKey, cerebrasModel, 8000, 60, objectMapper));

        return providers;
    }
}

