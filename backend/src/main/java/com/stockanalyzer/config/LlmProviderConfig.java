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
import java.util.Map;

/**
 * Registers all LLM providers in priority order for FallbackLlmClient.
 *
 * FREE providers — add API keys in Railway → Variables:
 *
 *  GEMINI_API_KEY      https://aistudio.google.com/apikey       (15 RPM flash + 30 RPM flash-lite)
 *  GROQ_API_KEY        https://console.groq.com/keys            30 RPM llama-3.3-70b + 30 RPM llama-3.1-8b
 *  OPENROUTER_API_KEY  https://openrouter.ai/keys               free model pool (multiple models)
 *  CEREBRAS_API_KEY    https://cloud.cerebras.ai                ultra-fast free tier
 *  SAMBANOVA_API_KEY   https://cloud.sambanova.ai               60 RPM Llama 3.3 70B free
 *  TOGETHER_API_KEY    https://api.together.xyz                 free tier models
 */
@Configuration
public class LlmProviderConfig {

    @Value("${gemini.api.key:}")       private String geminiKey;
    @Value("${gemini.api.url}")        private String geminiApiUrl;
    @Value("${gemini.max-tokens:8192}") private int   geminiMaxTokens;
    @Value("${gemini.max-retries:1}")  private int    geminiMaxRetries;

    @Value("${groq.api.key:}")         private String groqKey;
    @Value("${groq.model:llama-3.3-70b-versatile}") private String groqModel;

    @Value("${openrouter.api.key:}")   private String openrouterKey;
    @Value("${openrouter.model:meta-llama/llama-3.3-70b-instruct:free}") private String openrouterModel;

    @Value("${cerebras.api.key:}")     private String cerebrasKey;
    @Value("${cerebras.model:llama3.1-8b}") private String cerebrasModel;

    @Value("${sambanova.api.key:}")    private String sambanovaKey;
    @Value("${sambanova.model:Meta-Llama-3.3-70B-Instruct}") private String sambanovaModel;

    @Value("${together.api.key:}")     private String togetherKey;
    @Value("${together.model:meta-llama/Llama-3.3-70B-Instruct-Turbo-Free}") private String togetherModel;

    @Bean
    public List<LlmClient> llmProviders(WebClient geminiWebClient, ObjectMapper objectMapper) {
        List<LlmClient> providers = new ArrayList<>();

        // 1. Gemini 2.0 Flash — primary (Google Search grounding, 15 RPM free)
        providers.add(new GeminiClient(geminiWebClient, objectMapper, geminiKey, geminiApiUrl,
                "gemini-2.0-flash", geminiMaxTokens, geminiMaxRetries));

        // 2. Gemini 2.0 Flash Lite — separate quota (30 RPM free)
        providers.add(new GeminiClient(geminiWebClient, objectMapper, geminiKey, geminiApiUrl,
                "gemini-2.0-flash-lite", geminiMaxTokens, geminiMaxRetries));

        // 3. Groq llama-3.3-70b — very fast, free 30 RPM (large context, handles full prompt)
        providers.add(new OpenAiCompatibleClient(
                "Groq", "https://api.groq.com/openai",
                groqKey, groqModel, 8000, 60, objectMapper));

        // 4. Cerebras llama3.1-8b — ultra-fast free tier (small model, may give simpler output)
        providers.add(new OpenAiCompatibleClient(
                "Cerebras", "https://api.cerebras.ai",
                cerebrasKey, cerebrasModel, 8000, 60, objectMapper));

        // 5. SambaNova — free 60 RPM, force JSON output to avoid parse failures
        providers.add(new OpenAiCompatibleClient(
                "SambaNova", "https://api.sambanova.ai",
                sambanovaKey, sambanovaModel, 8000, 90, objectMapper,
                java.util.Map.of(), true));

        // 6. OpenRouter — free model pool, primary model
        providers.add(new OpenAiCompatibleClient(
                "OpenRouter", "https://openrouter.ai/api",
                openrouterKey, openrouterModel, 8000, 120, objectMapper,
                Map.of("HTTP-Referer", "https://stock-analyzer-neon.vercel.app",
                       "X-Title", "Stock Analyser")));

        // 7. OpenRouter — second free model (google/gemma-3-27b-it)
        providers.add(new OpenAiCompatibleClient(
                "OpenRouter", "https://openrouter.ai/api",
                openrouterKey, "google/gemma-3-27b-it:free", 8000, 120, objectMapper,
                Map.of("HTTP-Referer", "https://stock-analyzer-neon.vercel.app",
                       "X-Title", "Stock Analyser")));

        // 8. Together AI — free tier, force JSON
        providers.add(new OpenAiCompatibleClient(
                "Together", "https://api.together.xyz",
                togetherKey, togetherModel, 8000, 120, objectMapper,
                java.util.Map.of(), true));

        return providers;
    }
}
