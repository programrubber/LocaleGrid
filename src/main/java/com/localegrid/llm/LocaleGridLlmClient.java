package com.localegrid.llm;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI 호환 (/v1/chat/completions) REST API와 비동기 통신하는 HTTP 클라이언트.
 * 사내 호스팅 Qwen, vLLM, Ollama, LiteLLM 및 상용 LLM 엔드포인트를 지원합니다.
 */
public class LocaleGridLlmClient {
    private static final LocaleGridLlmClient INSTANCE = new LocaleGridLlmClient();

    private final HttpClient httpClient;

    public LocaleGridLlmClient() {
        this(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build());
    }

    public LocaleGridLlmClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public static LocaleGridLlmClient getInstance() {
        return INSTANCE;
    }

    /**
     * 엔드포인트 연결 및 모델 응답성을 테스트합니다.
     *
     * @return 왕복 소요 시간 (밀리초)
     */
    public CompletableFuture<Long> testConnection(
        String endpoint,
        String model,
        String apiKey,
        int timeoutSeconds
    ) {
        long startTime = System.currentTimeMillis();
        return sendChatCompletion(
            endpoint,
            model,
            apiKey,
            "Return only a valid JSON object.",
            "Return exactly this JSON object: {\"status\":\"ok\"}",
            0.0,
            timeoutSeconds
        ).thenApply(content -> {
            try {
                JSONObject result = new JSONObject(extractJsonBlock(content));
                if (!"ok".equalsIgnoreCase(result.optString("status"))) {
                    throw new RuntimeException("LLM 연결 테스트 응답 형식이 올바르지 않습니다.");
                }
                return System.currentTimeMillis() - startTime;
            } catch (JSONException e) {
                throw new RuntimeException("LLM 연결 테스트 응답이 JSON 형식이 아닙니다.", e);
            }
        });
    }

    private CompletableFuture<HttpResponse<String>> sendChatCompletionRequest(
        String endpoint,
        String model,
        String apiKey,
        String systemPrompt,
        String userPrompt,
        double temperature,
        int timeoutSeconds,
        boolean includeJsonMode,
        boolean disableThinking,
        int remainingFallbacks
    ) {
        String payload = buildChatCompletionPayload(
            model,
            systemPrompt,
            userPrompt,
            temperature,
            includeJsonMode,
            disableThinking
        );
        HttpRequest request = buildHttpRequest(endpoint, apiKey, payload, timeoutSeconds);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenCompose(response -> {
                if (remainingFallbacks <= 0) {
                    return CompletableFuture.completedFuture(response);
                }

                boolean nextJsonMode = includeJsonMode
                    && !isResponseFormatUnsupported(response.statusCode(), response.body());
                boolean nextDisableThinking = disableThinking
                    && !isThinkingControlUnsupported(response.statusCode(), response.body());
                if (nextJsonMode == includeJsonMode
                    && nextDisableThinking == disableThinking) {
                    return CompletableFuture.completedFuture(response);
                }

                return sendChatCompletionRequest(
                    endpoint,
                    model,
                    apiKey,
                    systemPrompt,
                    userPrompt,
                    temperature,
                    timeoutSeconds,
                    nextJsonMode,
                    nextDisableThinking,
                    remainingFallbacks - 1
                );
            });
    }

    /**
     * Chat Completion 요청을 전송하고 AI 응답 메시지 문자열을 반환합니다.
     */
    public CompletableFuture<String> sendChatCompletion(
        String endpoint,
        String model,
        String apiKey,
        String systemPrompt,
        String userPrompt,
        double temperature,
        int timeoutSeconds
    ) {
        try {
            return sendChatCompletionRequest(
                endpoint,
                model,
                apiKey,
                systemPrompt,
                userPrompt,
                temperature,
                timeoutSeconds,
                true,
                isQwenModel(model),
                2
            ).thenApply(response -> {
                int status = response.statusCode();
                String body = response.body();
                if (status >= 200 && status < 300) {
                    return parseCompletionContent(body);
                }
                String errorMessage = extractErrorMessage(body, status);
                throw new RuntimeException("LLM 요청 실패 (HTTP " + status + "): " + errorMessage);
            });
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public static String buildChatCompletionPayload(
        String model,
        String systemPrompt,
        String userPrompt,
        double temperature
    ) {
        return buildChatCompletionPayload(
            model,
            systemPrompt,
            userPrompt,
            temperature,
            true,
            isQwenModel(model)
        );
    }

    private static boolean isQwenModel(String model) {
        return model != null && model.toLowerCase(Locale.ROOT).contains("qwen");
    }

    static String buildChatCompletionPayload(
        String model,
        String systemPrompt,
        String userPrompt,
        double temperature,
        boolean disableThinking
    ) {
        return buildChatCompletionPayload(
            model,
            systemPrompt,
            userPrompt,
            temperature,
            true,
            disableThinking
        );
    }

    static String buildChatCompletionPayload(
        String model,
        String systemPrompt,
        String userPrompt,
        double temperature,
        boolean includeJsonMode,
        boolean disableThinking
    ) {
        JSONObject root = new JSONObject();
        root.put("model", model);
        root.put("temperature", Math.max(0.0, Math.min(2.0, temperature)));
        root.put("max_tokens", 1024);
        root.put("stream", false);
        if (includeJsonMode) {
            root.put("response_format", new JSONObject().put("type", "json_object"));
        }
        if (disableThinking) {
            root.put(
                "chat_template_kwargs",
                new JSONObject().put("enable_thinking", false)
            );
        }

        JSONArray messages = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt.trim());
            messages.put(sysMsg);
        }
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.put(userMsg);

        root.put("messages", messages);
        return root.toString();
    }

    static boolean isThinkingControlUnsupported(int statusCode, String responseBody) {
        return isUnsupportedOptionalField(
            statusCode,
            responseBody,
            "chat_template_kwargs",
            "enable_thinking"
        );
    }

    static boolean isResponseFormatUnsupported(int statusCode, String responseBody) {
        return isUnsupportedOptionalField(
            statusCode,
            responseBody,
            "response_format",
            "json_object"
        );
    }

    private static boolean isUnsupportedOptionalField(
        int statusCode,
        String responseBody,
        String... fieldNames
    ) {
        if (statusCode != 400 && statusCode != 422 || responseBody == null) {
            return false;
        }
        String normalized = responseBody.toLowerCase(Locale.ROOT);
        for (String fieldName : fieldNames) {
            if (normalized.contains(fieldName)) {
                return true;
            }
        }
        return false;
    }

    private HttpRequest buildHttpRequest(
        String endpoint,
        String apiKey,
        String jsonPayload,
        int timeoutSeconds
    ) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(endpoint.trim()))
            .header("Content-Type", "application/json; charset=utf-8")
            .timeout(Duration.ofSeconds(Math.max(3, timeoutSeconds)))
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey.trim());
        }

        return builder.build();
    }

    public static String parseCompletionContent(String responseJson) {
        try {
            JSONObject root = new JSONObject(responseJson);
            JSONArray choices = root.optJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject firstChoice = choices.getJSONObject(0);
                String finishReason = firstChoice.optString("finish_reason", "");
                if ("length".equalsIgnoreCase(finishReason)
                    || "max_tokens".equalsIgnoreCase(finishReason)) {
                    throw new RuntimeException(
                        "LLM 응답이 출력 토큰 한도에서 중단되었습니다. "
                            + "모델의 추론 모드 비활성화 지원 여부를 확인하세요."
                    );
                }
                if ("content_filter".equalsIgnoreCase(finishReason)) {
                    throw new RuntimeException("LLM 응답이 콘텐츠 필터에 의해 중단되었습니다.");
                }
                if ("tool_calls".equalsIgnoreCase(finishReason)
                    || "function_call".equalsIgnoreCase(finishReason)) {
                    throw new RuntimeException("LLM이 번역 JSON 대신 도구 호출을 반환했습니다.");
                }
                JSONObject message = firstChoice.optJSONObject("message");
                if (message != null) {
                    Object content = message.opt("content");
                    if (content instanceof String text && !text.isBlank()) {
                        return text;
                    }
                }
                Object legacyText = firstChoice.opt("text");
                if (legacyText instanceof String text && !text.isBlank()) {
                    return text;
                }
                throw new RuntimeException("LLM 응답에 번역 내용이 없습니다.");
            }
            throw new RuntimeException("응답 본문에 'choices[0].message.content' 필드가 없습니다.");
        } catch (JSONException e) {
            throw new RuntimeException("LLM 응답 JSON 파싱 실패: " + e.getMessage(), e);
        }
    }

    public static String extractErrorMessage(String body, int statusCode) {
        if (body == null || body.isBlank()) {
            return "상세 정보 없음 (HTTP 상태 코드: " + statusCode + ")";
        }
        try {
            JSONObject root = new JSONObject(body);
            if (root.has("error")) {
                Object err = root.get("error");
                if (err instanceof JSONObject errObj && errObj.has("message")) {
                    return compactMessage(errObj.opt("message"), 240);
                }
                return compactMessage(err, 240);
            }
        } catch (Exception ignored) {
        }
        return compactMessage(body, 240);
    }

    private static String compactMessage(Object value, int maxLength) {
        String compact = String.valueOf(value).replaceAll("\\s+", " ").trim();
        return compact.length() > maxLength
            ? compact.substring(0, maxLength - 3) + "..."
            : compact;
    }

    /**
     * 앞뒤 설명, 마크다운 코드 블록, 플레이스홀더가 포함된 응답에서
     * 마지막으로 완결된 유효 JSON 객체 문자열을 추출합니다.
     */
    public static String extractJsonBlock(String rawText) {
        List<String> candidates = extractJsonObjectCandidates(rawText);
        if (!candidates.isEmpty()) {
            return candidates.get(candidates.size() - 1);
        }
        return rawText == null || rawText.isBlank() ? "{}" : rawText.trim();
    }

    static List<String> extractJsonObjectCandidates(String rawText) {
        List<String> candidates = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            return candidates;
        }
        String text = rawText.trim();
        for (int start = 0; start < text.length(); start++) {
            if (text.charAt(start) != '{') {
                continue;
            }

            int end = findMatchingObjectEnd(text, start);
            if (end < 0) {
                continue;
            }

            String candidate = text.substring(start, end + 1).trim();
            try {
                new JSONObject(candidate);
                candidates.add(candidate);
                start = end;
            } catch (JSONException ignored) {
                // 설명문 속 {0}, {name} 등은 건너뛰고 다음 객체 후보를 계속 찾습니다.
            }
        }
        return candidates;
    }

    private static int findMatchingObjectEnd(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int index = start; index < text.length(); index++) {
            char current = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
                if (depth < 0) {
                    return -1;
                }
            }
        }
        return -1;
    }
}
