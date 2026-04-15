package com.stockanalyzer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockanalyzer.client.GeminiClient;
import com.stockanalyzer.client.LlmClient;
import com.stockanalyzer.client.OpenAiCompatibleClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers all LLM providers in priority order for FallbackLlmClient.
 *
 * FREE providers — add API keys in Railway → Variables:
 *
 *  GEMINI_API_KEY      https://aistudio.google.com/apikey       (gemini-2.0-flash 15 RPM + gemini-2.0-flash-lite 30 RPM)
 *  GROQ_API_KEY        https://console.groq.com/keys            30 RPM, fast
 *  OPENROUTER_API_KEY  https://openrouter.ai/keys               free model pool
 *  CEREBRAS_API_KEY    https://cloud.cerebras.ai                ultra-fast
 */
@Configuration
public class LlmProviderConfig {

    // ── Gemini ────────────────────────────────────────────────────────────────
    @Value("${gemini.api.key:}")
    private String geminiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.max-tokens:8192}")
    private int geminiMaxTokens;

    @Value("${gemini.max-retries:1}")
    private int geminiMaxRetries;

    // ── Groq ──────────────────────────────────────────────────────────────────
    @Value("${groq.api.key:}")
    private String groqKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    // ── OpenRouter ────────────────────────────────────────────────────────────
    @Value("${openrouter.api.key:}")
    private String openrouterKey;

    @Value("${openrouter.model:meta-llama/llama-3.3-70b-instruct:free}")
    private String openrouterModel;

    // ── Cerebras ──────────────────────────────────────────────────────────────
    @Value("${cerebras.api.key:}")
    private String cerebrasKey;

    @Value("${cerebras.model:llama-3.3-70b}")
    private String cerebrasModel;

    @Bean
    public List<LlmClient> llmProviders(WebClient geminiWebClient, ObjectMapper objectMapper) {
        List<LlmClient> providers = new ArrayList<>();

        // 1. Gemini 2.0 Flash — primary (Google Search grounding, 15 RPM free)
        providers.add(new GeminiClient(geminiWebClient, objectMapper, geminiKey, geminiApiUrl,
                "gemini-2.0-flash", geminiMaxTokens, geminiMaxRetries));

        // 2. Gemini 2.0 Flash Lite — second Gemini (separate quota: 30 RPM free)
        //    Falls back here if gemini-2.0-flash is rate-limited
        providers.add(new GeminiClient(geminiWebClient, objectMapper, geminiKey, geminiApiUrl,
                "gemini-2.0-flash-lite", geminiMaxTokens, geminiMaxRetries));

        // 3. Groq — very fast, free 30 RPM (no live web data, training cutoff)
        providers.add(new OpenAiCompatibleClient(
                "Groq", "https://api.groq.com/openai",
                groqKey, groqModel, 8000, 60, objectMapper));

        // 4. OpenRouter — routes to many free models (no live web data)
        providers.add(new OpenAiCompatibleClient(
                "OpenRouter", "https://openrouter.ai/api",
                openrouterKey, openrouterModel, 8000, 120, objectMapper,
                java.util.Map.of("HTTP-Referer", "https://stock-analyzer-neon.vercel.app",
                                 "X-Title", "Stock Analyser")));

        // 5. Cerebras — ultra-fast inference, free tier (no live web data)
        providers.add(new OpenAiCompatibleClient(
                "Cerebras", "https://api.cerebras.ai",
                cerebrasKey, cerebrasModel, 8000, 60, objectMapper));

        return providers;
    }
}
