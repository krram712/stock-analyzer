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

import java.util.ArrayList;
import java.util.List;

/**
 * Low-level client for the Anthropic Messages API.
 *
 * Handles:
 *  - Request construction (model, system prompt, tools)
 *  - The agentic tool-use loop (runs until stop_reason = "end_turn")
 *  - JSON extraction from the final assistant turn
 *  - Retry on transient 5xx errors
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnthropicClient {

    private final WebClient anthropicWebClient;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Value("${anthropic.api.url}")
    private String apiUrl;

    @Value("${anthropic.model}")
    private String model;

    @Value("${anthropic.max-tokens}")
    private int maxTokens;

    @Value("${anthropic.api-version}")
    private String apiVersion;

    @Value("${anthropic.beta}")
    private String betaHeader;

    @Value("${anthropic.max-retries:2}")
    private int maxRetries;

    private static final int MAX_TOOL_LOOP_ITERATIONS = 8;

    /**
     * Runs the full agentic loop with web-search tool enabled.
     *
     * @param systemPrompt  The system instruction for Claude
     * @param userMessage   The user-facing request (ticker + horizon)
     * @return              Raw JSON string produced by Claude
     */
    // claude-haiku-4-5 pricing (per million tokens)
    private static final double INPUT_COST_PER_M  = 0.80;
    private static final double OUTPUT_COST_PER_M = 4.00;

    private long totalInputTokens  = 0;
    private long totalOutputTokens = 0;

    public String complete(String systemPrompt, String userMessage) {
        List<ObjectNode> messages = new ArrayList<>();
        messages.add(buildUserMessage(userMessage));

        long analysisInputTokens  = 0;
        long analysisOutputTokens = 0;
        int iteration = 0;

        while (iteration < MAX_TOOL_LOOP_ITERATIONS) {
            iteration++;
            log.debug("Anthropic API call – iteration {}", iteration);

            String rawResponse = callWithRetry(buildRequest(systemPrompt, messages));

            JsonNode response;
            try {
                response = objectMapper.readTree(rawResponse);
            } catch (Exception e) {
                throw new AnalysisException("Failed to parse Anthropic response: " + e.getMessage(), e);
            }

            String stopReason = response.path("stop_reason").asText("end_turn");
            JsonNode contentArray = response.path("content");

            // ── Track token usage ─────────────────────────────────────────────
            JsonNode usage = response.path("usage");
            long inTok  = usage.path("input_tokens").asLong(0);
            long outTok = usage.path("output_tokens").asLong(0);
            analysisInputTokens  += inTok;
            analysisOutputTokens += outTok;

            // ── Agentic loop: Claude used a tool ─────────────────────────────
            if ("tool_use".equals(stopReason)) {
                // 1. Append Claude's assistant turn
                ObjectNode assistantMsg = objectMapper.createObjectNode();
                assistantMsg.put("role", "assistant");
                assistantMsg.set("content", contentArray);
                messages.add(assistantMsg);

                // 2. Build tool_result blocks and feed them back
                ArrayNode toolResults = buildToolResults(contentArray);
                if (!toolResults.isEmpty()) {
                    ObjectNode toolResultMsg = objectMapper.createObjectNode();
                    toolResultMsg.put("role", "user");
                    toolResultMsg.set("content", toolResults);
                    messages.add(toolResultMsg);
                }
                continue; // loop
            }

            // ── Finished: extract text (should be our JSON) ──────────────────
            if ("end_turn".equals(stopReason) || "stop_sequence".equals(stopReason)) {
                String text = extractText(contentArray);
                logCost(analysisInputTokens, analysisOutputTokens, iteration);
                return sanitiseJson(text);
            }

            // ── Response truncated ────────────────────────────────────────────
            if ("max_tokens".equals(stopReason)) {
                throw new AnalysisException("Response was too long and got cut off. Please try again.", 500);
            }

            // ── Unexpected stop reason ────────────────────────────────────────
            log.warn("Unexpected stop_reason '{}'; attempting to extract text anyway", stopReason);
            String text = extractText(contentArray);
            if (!text.isBlank()) return sanitiseJson(text);

            throw new AnalysisException("Anthropic returned unexpected stop_reason: " + stopReason);
        }

        throw new AnalysisException("Anthropic tool loop exceeded max iterations (" + MAX_TOOL_LOOP_ITERATIONS + ")");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void logCost(long inputTokens, long outputTokens, int iterations) {
        double inputCost  = (inputTokens  / 1_000_000.0) * INPUT_COST_PER_M;
        double outputCost = (outputTokens / 1_000_000.0) * OUTPUT_COST_PER_M;
        double totalCost  = inputCost + outputCost;

        totalInputTokens  += inputTokens;
        totalOutputTokens += outputTokens;
        double sessionCost = (totalInputTokens  / 1_000_000.0) * INPUT_COST_PER_M
                           + (totalOutputTokens / 1_000_000.0) * OUTPUT_COST_PER_M;

        log.info("━━━ Analysis cost ━━━ iterations={} | input={} tokens | output={} tokens | cost=${} | session total=${}",
                iterations, inputTokens, outputTokens,
                String.format("%.4f", totalCost),
                String.format("%.4f", sessionCost));
    }

    private ObjectNode buildRequest(String systemPrompt, List<ObjectNode> messages) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", systemPrompt);

        ArrayNode msgsArr = objectMapper.createArrayNode();
        messages.forEach(msgsArr::add);
        body.set("messages", msgsArr);

        // Enable Anthropic's built-in web search tool
        ArrayNode tools = objectMapper.createArrayNode();
        ObjectNode webSearch = objectMapper.createObjectNode();
        webSearch.put("type", "web_search_20250305");
        webSearch.put("name", "web_search");
        tools.add(webSearch);
        body.set("tools", tools);

        return body;
    }

    private ObjectNode buildUserMessage(String content) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("role", "user");
        msg.put("content", content);
        return msg;
    }

    /**
     * For web_search (a server-side tool), Anthropic may already include
     * the search results in the same response content array as
     * "web_search_result_20250305" blocks. We simply echo back a tool_result
     * confirming receipt so Claude can continue.
     */
    private ArrayNode buildToolResults(JsonNode contentArray) {
        ArrayNode results = objectMapper.createArrayNode();
        if (!contentArray.isArray()) return results;

        for (JsonNode block : contentArray) {
            String type = block.path("type").asText();
            if ("tool_use".equals(type)) {
                ObjectNode result = objectMapper.createObjectNode();
                result.put("type", "tool_result");
                result.put("tool_use_id", block.path("id").asText());

                // Carry search result content if present in the response
                JsonNode inputNode = block.path("input");
                String query = inputNode.path("query").asText("search executed");
                result.put("content", "Search for: " + query + " — completed by Anthropic server.");
                results.add(result);
            }
        }
        return results;
    }

    private String extractText(JsonNode contentArray) {
        if (!contentArray.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : contentArray) {
            if ("text".equals(block.path("type").asText())) {
                sb.append(block.path("text").asText());
            }
        }
        return sb.toString().trim();
    }

    /**
     * Strip markdown fences and extract the JSON object from Claude's response,
     * even if it contains surrounding explanation text.
     */
    private String sanitiseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AnalysisException("Claude returned an empty response.");
        }

        String cleaned = raw.trim();

        // 1. Strip ```json ... ``` or ``` ... ``` fences
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) cleaned = cleaned.substring(firstNewline + 1).trim();
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.lastIndexOf("```")).trim();
        }

        // 2. Try parsing as-is
        try {
            objectMapper.readTree(cleaned);
            return cleaned;
        } catch (Exception ignored) { /* fall through to extraction */ }

        // 3. Extract the outermost JSON object { ... } from mixed text
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start != -1 && end > start) {
            String extracted = cleaned.substring(start, end + 1);
            try {
                objectMapper.readTree(extracted);
                log.debug("Extracted JSON from mixed-text response (trimmed {} leading + {} trailing chars)",
                        start, cleaned.length() - end - 1);
                return extracted;
            } catch (Exception ignored) { /* fall through to error */ }
        }

        log.error("Claude output is not valid JSON. Full response ({} chars):\n{}", raw.length(), raw);
        throw new AnalysisException("Claude did not return valid JSON. Please try again.");
    }

    private String callWithRetry(ObjectNode body) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return anthropicWebClient.post()
                        .uri(apiUrl)
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", apiVersion)
                        .header("anthropic-beta", betaHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body.toString())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

            } catch (WebClientResponseException ex) {
                int status = ex.getStatusCode().value();
                String body2 = ex.getResponseBodyAsString();
                log.error("Anthropic API error {} (attempt {}/{}): {}", status, attempts, maxRetries + 1, body2);

                if (status == HttpStatus.UNAUTHORIZED.value()) {
                    throw new AnalysisException("Invalid Anthropic API key. Check your ANTHROPIC_API_KEY environment variable.", 401);
                }
                if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                    if (attempts <= maxRetries) {
                        // Honour Retry-After header if present, otherwise back off 60s
                        String retryAfter = ex.getHeaders().getFirst("retry-after");
                        long waitSec = retryAfter != null ? Long.parseLong(retryAfter) : 60L;
                        log.warn("Anthropic rate limit hit (attempt {}). Waiting {}s...", attempts, waitSec);
                        try { Thread.sleep(waitSec * 1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        continue;
                    }
                    throw new AnalysisException("Anthropic rate limit reached. Please wait a minute and try again.", 429);
                }
                if (status >= 500 && attempts <= maxRetries) {
                    log.warn("Retrying after server error ({})...", status);
                    try { Thread.sleep(2000L * attempts); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }
                throw new AnalysisException("Anthropic API returned " + status + ": " + body2);

            } catch (Exception ex) {
                if (attempts <= maxRetries) {
                    log.warn("Retrying after transient error: {}", ex.getMessage());
                    try { Thread.sleep(2000L * attempts); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }
                throw new AnalysisException("Failed to call Anthropic API: " + ex.getMessage(), ex);
            }
        }
    }
}