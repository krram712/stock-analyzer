package com.stockanalyzer.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stockanalyzer.exception.AnalysisException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Low-level client for the Google Gemini API (generateContent).
 *
 * Uses Google Search grounding so Gemini fetches live financial data
 * before generating the analysis — equivalent to Claude's web-search tool,
 * but free on the Gemini 1.5 Flash free tier.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.max-tokens:8192}")
    private int maxTokens;

    @Value("${gemini.max-retries:2}")
    private int maxRetries;

    private long totalInputTokens  = 0;
    private long totalOutputTokens = 0;

    /**
     * Calls Gemini with Google Search grounding enabled.
     * Single-shot: Gemini handles the search internally and returns the final answer.
     *
     * @param systemPrompt  The system instruction
     * @param userMessage   The user request (ticker + horizon)
     * @return              Raw JSON string produced by Gemini
     */
    public String complete(String systemPrompt, String userMessage) {
        String url = apiUrl.replace("{model}", model);
        ObjectNode body = buildRequest(systemPrompt, userMessage);

        log.debug("Calling Gemini API (model={})...", model);
        String rawResponse = callWithRetry(body, url);

        JsonNode response;
        try {
            response = objectMapper.readTree(rawResponse);
        } catch (Exception e) {
            throw new AnalysisException("Failed to parse Gemini response: " + e.getMessage(), e);
        }

        // Check for top-level API error
        if (response.has("error")) {
            JsonNode error = response.get("error");
            String errorMsg = error.path("message").asText("Unknown Gemini error");
            int errorCode  = error.path("code").asInt(500);
            throw new AnalysisException("Gemini API error: " + errorMsg, errorCode);
        }

        // Extract text from candidates[0]
        JsonNode candidates = response.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new AnalysisException("Gemini returned no candidates in response.");
        }

        JsonNode firstCandidate = candidates.get(0);
        String finishReason = firstCandidate.path("finishReason").asText("STOP");

        if ("MAX_TOKENS".equals(finishReason)) {
            throw new AnalysisException("Response was too long and got cut off. Please try again.", 500);
        }
        if ("SAFETY".equals(finishReason)) {
            throw new AnalysisException("Gemini blocked the response for safety reasons.", 400);
        }

        JsonNode parts = firstCandidate.path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new AnalysisException("Gemini returned empty content in response.");
        }

        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.has("text")) {
                sb.append(part.path("text").asText());
            }
        }
        String text = sb.toString().trim();

        // Log token usage
        JsonNode usage = response.path("usageMetadata");
        long inTok  = usage.path("promptTokenCount").asLong(0);
        long outTok = usage.path("candidatesTokenCount").asLong(0);
        totalInputTokens  += inTok;
        totalOutputTokens += outTok;
        log.info("━━━ Gemini usage ━━━ input={} | output={} | session total={} tokens",
                inTok, outTok, totalInputTokens + totalOutputTokens);

        return sanitiseJson(text);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ObjectNode buildRequest(String systemPrompt, String userMessage) {
        ObjectNode body = objectMapper.createObjectNode();

        // System instruction
        ObjectNode sysInstruction = objectMapper.createObjectNode();
        ArrayNode  sysParts       = objectMapper.createArrayNode();
        ObjectNode sysPart        = objectMapper.createObjectNode();
        sysPart.put("text", systemPrompt);
        sysParts.add(sysPart);
        sysInstruction.set("parts", sysParts);
        body.set("system_instruction", sysInstruction);

        // Contents (user turn)
        ArrayNode  contents    = objectMapper.createArrayNode();
        ObjectNode userContent = objectMapper.createObjectNode();
        userContent.put("role", "user");
        ArrayNode  userParts   = objectMapper.createArrayNode();
        ObjectNode userPart    = objectMapper.createObjectNode();
        userPart.put("text", userMessage);
        userParts.add(userPart);
        userContent.set("parts", userParts);
        contents.add(userContent);
        body.set("contents", contents);

        // Google Search grounding tool (live financial data)
        ArrayNode  tools      = objectMapper.createArrayNode();
        ObjectNode searchTool = objectMapper.createObjectNode();
        searchTool.set("google_search", objectMapper.createObjectNode());
        tools.add(searchTool);
        body.set("tools", tools);

        // Generation config
        ObjectNode genConfig = objectMapper.createObjectNode();
        genConfig.put("maxOutputTokens", maxTokens);
        genConfig.put("temperature", 0.1);
        body.set("generationConfig", genConfig);

        return body;
    }

    /**
     * Strip markdown fences and extract the JSON object from the response.
     */
    private String sanitiseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AnalysisException("Gemini returned an empty response.");
        }

        String cleaned = raw.trim();

        // 1. Strip ```json … ``` or ``` … ``` fences
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) cleaned = cleaned.substring(firstNewline + 1).trim();
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.lastIndexOf("```")).trim();
        }

        // 2. Try parsing as-is
        try {
            objectMapper.readTree(cleaned);
            return cleaned;
        } catch (Exception ignored) { /* fall through */ }

        // 3. Extract outermost { … } from mixed text
        int start = cleaned.indexOf('{');
        int end   = cleaned.lastIndexOf('}');
        if (start != -1 && end > start) {
            String extracted = cleaned.substring(start, end + 1);
            try {
                objectMapper.readTree(extracted);
                log.debug("Extracted JSON from mixed-text response (trimmed {} leading chars)", start);
                return extracted;
            } catch (Exception ignored) { /* fall through */ }
        }

        log.error("Gemini output is not valid JSON. Full response ({} chars):\n{}", raw.length(), raw);
        throw new AnalysisException("Gemini did not return valid JSON. Please try again.");
    }

    private String callWithRetry(ObjectNode body, String url) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return geminiWebClient.post()
                        .uri(url + "?key=" + apiKey)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .bodyValue(body.toString())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

            } catch (WebClientResponseException ex) {
                int    status       = ex.getStatusCode().value();
                String responseBody = ex.getResponseBodyAsString();
                log.error("Gemini API error {} (attempt {}/{}): {}", status, attempts, maxRetries + 1, responseBody);

                if (status == HttpStatus.UNAUTHORIZED.value() || status == 403) {
                    throw new AnalysisException("Invalid Gemini API key. Check your GEMINI_API_KEY environment variable.", 401);
                }
                if (status == 400 || status == 404) {
                    try {
                        JsonNode errNode = objectMapper.readTree(responseBody);
                        String msg = errNode.path("error").path("message").asText(responseBody);
                        throw new AnalysisException("Gemini API error: " + msg, status);
                    } catch (AnalysisException ae) { throw ae; }
                    catch (Exception e) { throw new AnalysisException("Gemini API returned " + status + ": " + responseBody, status); }
                }
                if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                    if (attempts <= maxRetries) {
                        // Honour Retry-After header; fall back to exponential backoff
                        long waitMs = resolveRetryAfterMs(ex, attempts);
                        log.warn("Gemini rate limit hit (attempt {}/{}). Waiting {}s before retry...",
                                attempts, maxRetries + 1, waitMs / 1000);
                        sleep(waitMs);
                        continue;
                    }
                    throw new AnalysisException(
                            "Gemini free-tier rate limit reached. Please wait ~60 seconds and try again.", 429);
                }
                if (status >= 500 && attempts <= maxRetries) {
                    long waitMs = 3000L * attempts; // 3s, 6s, 9s…
                    log.warn("Retrying after Gemini server error {} in {}ms...", status, waitMs);
                    sleep(waitMs);
                    continue;
                }
                throw new AnalysisException("Gemini API returned " + status + ": " + responseBody);

            } catch (AnalysisException ae) {
                throw ae;
            } catch (Exception ex) {
                if (attempts <= maxRetries) {
                    long waitMs = 3000L * attempts;
                    log.warn("Retrying after transient error in {}ms: {}", waitMs, ex.getMessage());
                    sleep(waitMs);
                    continue;
                }
                throw new AnalysisException("Failed to call Gemini API: " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * Reads Retry-After header (seconds) from a 429 response.
     * Falls back to exponential backoff: 65s, 90s, 120s, …
     */
    private long resolveRetryAfterMs(WebClientResponseException ex, int attempt) {
        String retryAfter = ex.getHeaders().getFirst("Retry-After");
        if (retryAfter != null) {
            try { return Long.parseLong(retryAfter.trim()) * 1000L; } catch (NumberFormatException ignored) {}
        }
        // Gemini free tier resets every 60s; add a small buffer + jitter per attempt
        return 65_000L + (long)(attempt - 1) * 30_000L;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}

