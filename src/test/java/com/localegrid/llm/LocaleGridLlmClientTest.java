package com.localegrid.llm;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocaleGridLlmClientTest {

    @Test
    void buildChatCompletionPayload_createsValidJson() {
        String payload = LocaleGridLlmClient.buildChatCompletionPayload(
            "qwen3.6-27b",
            "System instruction",
            "User request",
            0.2
        );

        JSONObject json = new JSONObject(payload);
        assertEquals("qwen3.6-27b", json.getString("model"));
        assertEquals(0.2, json.getDouble("temperature"), 0.001);
        assertEquals(1024, json.getInt("max_tokens"));
        assertFalse(json.getBoolean("stream"));
        assertEquals("json_object", json.getJSONObject("response_format").getString("type"));
        assertFalse(
            json.getJSONObject("chat_template_kwargs").getBoolean("enable_thinking")
        );
        assertEquals(2, json.getJSONArray("messages").length());
        assertEquals("system", json.getJSONArray("messages").getJSONObject(0).getString("role"));
        assertEquals("System instruction", json.getJSONArray("messages").getJSONObject(0).getString("content"));
        assertEquals("user", json.getJSONArray("messages").getJSONObject(1).getString("role"));
        assertEquals("User request", json.getJSONArray("messages").getJSONObject(1).getString("content"));
    }

    @Test
    void parseCompletionContent_extractsFirstChoice() {
        String sampleResponse = """
            {
              "id": "chatcmpl-123",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "{\\"ja\\": \\"保存\\"}"
                  },
                  "finish_reason": "stop"
                }
              ]
            }
            """;

        String content = LocaleGridLlmClient.parseCompletionContent(sampleResponse);
        assertEquals("{\"ja\": \"保存\"}", content);
    }

    @Test
    void parseCompletionContent_throwsWhenChoicesEmpty() {
        String sampleResponse = "{\"choices\": []}";
        assertThrows(RuntimeException.class, () -> LocaleGridLlmClient.parseCompletionContent(sampleResponse));
    }

    @Test
    void parseCompletionContent_rejectsResponseTruncatedByTokenLimit() {
        String sampleResponse = """
            {
              "choices": [
                {
                  "message": {"role": "assistant", "content": "Thinking Process..."},
                  "finish_reason": "length"
                }
              ]
            }
            """;

        RuntimeException error = assertThrows(
            RuntimeException.class,
            () -> LocaleGridLlmClient.parseCompletionContent(sampleResponse)
        );
        assertTrue(error.getMessage().contains("출력 토큰 한도"));
    }

    @Test
    void parseCompletionContent_rejectsBlankContent() {
        String sampleResponse = """
            {
              "choices": [
                {
                  "message": {"role": "assistant", "content": ""},
                  "finish_reason": "stop"
                }
              ]
            }
            """;

        RuntimeException error = assertThrows(
            RuntimeException.class,
            () -> LocaleGridLlmClient.parseCompletionContent(sampleResponse)
        );
        assertTrue(error.getMessage().contains("번역 내용이 없습니다"));
    }

    @Test
    void extractErrorMessage_extractsNestedMessage() {
        String errorJson = """
            {
              "error": {
                "message": "Model 'invalid-model' not found",
                "type": "invalid_request_error"
              }
            }
            """;

        String errorMsg = LocaleGridLlmClient.extractErrorMessage(errorJson, 404);
        assertEquals("Model 'invalid-model' not found", errorMsg);
    }

    @Test
    void extractJsonBlock_handlesMarkdownFences() {
        String rawWithFence = """
            Here is your translation:
            ```json
            {
              "ja": "こんにちは",
              "vi": "Xin chào"
            }
            ```
            Hope this helps!
            """;

        String extracted = LocaleGridLlmClient.extractJsonBlock(rawWithFence);
        JSONObject obj = new JSONObject(extracted);
        assertEquals("こんにちは", obj.getString("ja"));
        assertEquals("Xin chào", obj.getString("vi"));
    }

    @Test
    void extractJsonBlock_handlesRawJsonWithoutFences() {
        String raw = "{\"ja\": \"テスト\"}";
        String extracted = LocaleGridLlmClient.extractJsonBlock(raw);
        JSONObject obj = new JSONObject(extracted);
        assertEquals("テスト", obj.getString("ja"));
    }

    @Test
    void extractJsonBlock_skipsReasoningPlaceholdersBeforeFinalJson() {
        String raw = """
            Thinking Process:
            Preserve placeholders such as {0} and {name}.
            Example input: {not-json}.
            Final answer:
            {"ko": "비밀번호를 잊으셨나요?", "ja": "パスワードをお忘れですか？"}
            """;

        JSONObject obj = new JSONObject(LocaleGridLlmClient.extractJsonBlock(raw));
        assertEquals("비밀번호를 잊으셨나요?", obj.getString("ko"));
        assertEquals("パスワードをお忘れですか？", obj.getString("ja"));
    }

    @Test
    void extractJsonBlock_preservesBracesAndEscapesInsideJsonStrings() {
        String raw = "Result: {\"ja\": \"保存 {0} \\\"{name}\\\"\"}";

        JSONObject obj = new JSONObject(LocaleGridLlmClient.extractJsonBlock(raw));
        assertEquals("保存 {0} \"{name}\"", obj.getString("ja"));
    }

    @Test
    void fallbackPayloadKeepsJsonModeButOmitsThinkingExtension() {
        JSONObject json = new JSONObject(
            LocaleGridLlmClient.buildChatCompletionPayload(
                "generic-model",
                "System instruction",
                "User request",
                0.2,
                false
            )
        );

        assertFalse(json.has("chat_template_kwargs"));
        assertEquals("json_object", json.getJSONObject("response_format").getString("type"));
    }

    @Test
    void genericModelPayloadOmitsQwenThinkingExtension() {
        JSONObject json = new JSONObject(
            LocaleGridLlmClient.buildChatCompletionPayload(
                "gpt-4o-mini",
                "System instruction",
                "User request",
                0.2
            )
        );

        assertFalse(json.has("chat_template_kwargs"));
        assertEquals("json_object", json.getJSONObject("response_format").getString("type"));
    }

    @Test
    void detectsUnsupportedThinkingControlResponse() {
        assertTrue(LocaleGridLlmClient.isThinkingControlUnsupported(
            400,
            "{\"error\":{\"message\":\"Unknown field chat_template_kwargs\"}}"
        ));
        assertTrue(LocaleGridLlmClient.isThinkingControlUnsupported(
            422,
            "enable_thinking is not permitted"
        ));
        assertFalse(LocaleGridLlmClient.isThinkingControlUnsupported(500, "server error"));
    }

    @Test
    void detectsUnsupportedResponseFormat() {
        assertTrue(LocaleGridLlmClient.isResponseFormatUnsupported(
            400,
            "Unknown field response_format"
        ));
        assertTrue(LocaleGridLlmClient.isResponseFormatUnsupported(
            422,
            "json_object is not supported"
        ));
        assertFalse(LocaleGridLlmClient.isResponseFormatUnsupported(401, "response_format"));
    }

    @Test
    void baselinePayloadOmitsBothOptionalCompatibilityFields() {
        JSONObject json = new JSONObject(
            LocaleGridLlmClient.buildChatCompletionPayload(
                "legacy-model",
                "System instruction",
                "User request",
                0.2,
                false,
                false
            )
        );

        assertFalse(json.has("response_format"));
        assertFalse(json.has("chat_template_kwargs"));
        assertFalse(json.getBoolean("stream"));
    }
}
