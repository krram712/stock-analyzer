package com.stockanalyzer.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stockanalyzer.exception.AnalysisException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * Generic OpenAI-compatible client — works with Perplexity (sonar models,
 * which have built-in web search) and Groq (llama models, fast inference).
 *
 * Both providers expose the same POST /v1/chat/completions endpoint with
 * Bearer token auth and the standard messages[] request format.
 */
@Slf4j
public class OpenAiCompatibleClient implements LlmClient {

    private final String providerName;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int    maxTokens;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    private final boolean forceJsonFormat;

    public OpenAiCompatibleClient(String providerName,
                                   String baseUrl,
                                   String apiKey,
                                   String model,
                                   int    maxTokens,
                                   int    timeoutSeconds,
                                   ObjectMapper objectMapper) {
        this(providerName, baseUrl, apiKey, model, maxTokens, timeoutSeconds, objectMapper, java.util.Map.of(), false);
    }

    public OpenAiCompatibleClient(String providerName,
                                   String baseUrl,
                                   String apiKey,
                                   String model,
                                   int    maxTokens,
                                   int    timeoutSeconds,
                                   ObjectMapper objectMapper,
                                   java.util.Map<String, String> extraHeaders) {
        this(providerName, baseUrl, apiKey, model, maxTokens, timeoutSeconds, objectMapper, extraHeaders, false);
    }

    public OpenAiCompatibleClient(String providerName,
                                   String baseUrl,
                                   String apiKey,
                                   String model,
                                   int    maxTokens,
                                   int    timeoutSeconds,
                                   ObjectMapper objectMapper,
                                   java.util.Map<String, String> extraHeaders,
                                   boolean forceJsonFormat) {
        this.providerName    = providerName;
        this.baseUrl         = baseUrl;
        this.apiKey          = apiKey;
        this.model           = model;
        this.maxTokens       = maxTokens;
        this.objectMapper    = objectMapper;
        this.forceJsonFormat = forceJsonFormat;
        var builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024));
        extraHeaders.forEach(builder::defaultHeader);
        this.webClient = builder.build();
    }

    @Override public String getProviderName() { return providerName + "(" + model + ")"; }
    @Override public boolean isAvailable()    { return apiKey != null && !apiKey.isBlank(); }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        if (!isAvailable()) {
            throw new AnalysisException(providerName + " API key not configured.", 503);
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.1);
        body.put("max_tokens", maxTokens);

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode sys = objectMapper.createObjectNode();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        messages.add(sys);

        ObjectNode user = objectMapper.createObjectNode();
        user.put("role", "user");
        user.put("content", userMessage);
        messages.add(user);

        body.set("messages", messages);

        // Force JSON output for providers that support it (reduces parse failures)
        if (forceJsonFormat) {
            ObjectNode fmt = objectMapper.createObjectNode();
            fmt.put("type", "json_object");
            body.set("response_format", fmt);
        }

        log.info("Calling {} API (model={})...", providerName, model);

        String rawResponse;
        try {
            rawResponse = webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(120));
        } catch (WebClientResponseException ex) {
            int status = ex.getStatusCode().value();
            log.error("{} API error {}: {}", providerName, status, ex.getResponseBodyAsString());
            if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw new AnalysisException(providerName + " rate limit reached. Trying next provider...", 429);
            }
            if (status == HttpStatus.UNAUTHORIZED.value() || status == 403) {
                throw new AnalysisException("Invalid " + providerName + " API key.", 401);
            }
            throw new AnalysisException(providerName + " API error " + status + ": " + ex.getResponseBodyAsString(), status);
        } catch (Exception ex) {
            log.error("{} call failed: {}", providerName, ex.getMessage());
            throw new AnalysisException(providerName + " call failed: " + ex.getMessage(), 503);
        }

        // Parse OpenAI-compatible response
        try {
            JsonNode root = objectMapper.readTree(rawResponse);

            // Check for error object
            if (root.has("error")) {
                String msg = root.path("error").path("message").asText(rawResponse);
                throw new AnalysisException(providerName + " error: " + msg);
            }

            String text = root.path("choices").get(0)
                              .path("message").path("content").asText("").trim();

            if (text.isBlank()) {
                throw new AnalysisException(providerName + " returned empty content.");
            }

            log.info("{} response received ({} chars)", providerName, text.length());
            return extractJson(text);

        } catch (AnalysisException ae) {
            throw ae;
        } catch (Exception e) {
            throw new AnalysisException("Failed to parse " + providerName + " response: " + e.getMessage());
        }
    }

    // ── JSON extraction (same as GeminiClient.sanitiseJson) ──────────────────

    private String extractJson(String raw) {
        String cleaned = raw.trim();

        // Strip ```json … ``` fences
        if (cleaned.startsWith("```")) {
            int nl = cleaned.indexOf('\n');
            if (nl != -1) cleaned = cleaned.substring(nl + 1).trim();
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.lastIndexOf("```")).trim();
        }

        // Try as-is
        try { objectMapper.readTree(cleaned); return cleaned; }
        catch (Exception ignored) {}

        // Extract outermost { … }
        int start = cleaned.indexOf('{');
        int end   = cleaned.lastIndexOf('}');
        if (start != -1 && end > start) {
            String extracted = cleaned.substring(start, end + 1);
            try { objectMapper.readTree(extracted); return extracted; }
            catch (Exception ignored) {}
        }

        log.error("{} output is not valid JSON ({} chars)", providerName, raw.length());
        throw new AnalysisException(providerName + " did not return valid JSON. Please try again.");
    }
}

