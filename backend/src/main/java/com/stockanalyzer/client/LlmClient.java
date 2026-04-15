package com.stockanalyzer.client;

/**
 * Common interface for all LLM providers (Gemini, Perplexity, Groq, …).
 * FallbackLlmClient iterates through registered providers on 429/503.
 */
public interface LlmClient {

    /**
     * Send a system prompt + user message and return the raw JSON string.
     *
     * @throws com.stockanalyzer.exception.AnalysisException with statusCode 429
     *         when the provider's rate limit is reached (signals fallback).
     */
    String complete(String systemPrompt, String userMessage);

    /** Human-readable name used in logs. */
    String getProviderName();

    /** Returns true if this provider is configured (API key present). */
    boolean isAvailable();
}

