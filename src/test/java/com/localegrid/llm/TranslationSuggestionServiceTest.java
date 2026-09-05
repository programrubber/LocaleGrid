package com.localegrid.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TranslationSuggestionServiceTest {

    @Test
    void buildUserPrompt_includesAllReferencesAndTargets() {
        String key = "common.btn.save";
        Map<String, String> references = Map.of(
            "ko", "저장하기",
            "en", "Save Changes"
        );
        List<String> targets = List.of("ja", "vi");

        String prompt = TranslationSuggestionService.buildUserPrompt(key, references, targets);

        assertTrue(prompt.contains("common.btn.save"));
        assertTrue(prompt.contains("ko: 저장하기"));
        assertTrue(prompt.contains("en: Save Changes"));
        assertTrue(prompt.contains("- ja"));
        assertTrue(prompt.contains("- vi"));
    }

    @Test
    void parseSuggestions_parsesValidJson() {
        String raw = """
            ```json
            {
              "ja": "保存する",
              "vi": "Lưu thay đổi"
            }
            ```
            """;

        Map<String, String> suggestions = TranslationSuggestionService.parseSuggestions(
            raw,
            List.of("ja", "vi"),
            Map.of("ko", "저장하기")
        );

        assertEquals(2, suggestions.size());
        assertEquals("保存する", suggestions.get("ja"));
        assertEquals("Lưu thay đổi", suggestions.get("vi"));
    }

    @Test
    void parseSuggestions_ignoresUnrequestedLocales() {
        String raw = "{\"ja\": \"はい\", \"de\": \"Ja\", \"fr\": \"Oui\"}";
        Map<String, String> suggestions = TranslationSuggestionService.parseSuggestions(
            raw,
            List.of("ja"),
            Map.of("ko", "예")
        );

        assertEquals(1, suggestions.size());
        assertEquals("はい", suggestions.get("ja"));
        assertNull(suggestions.get("de"));
    }

    @Test
    void parseSuggestions_supportsTranslationsWrapperFromDesignContract() {
        String raw = """
            {
              "translations": {
                "ko": "비밀번호를 잊으셨나요?",
                "ja": "パスワードをお忘れですか？"
              }
            }
            """;

        Map<String, String> suggestions = TranslationSuggestionService.parseSuggestions(
            raw,
            List.of("ko", "ja"),
            Map.of("en", "Forgot password?")
        );

        assertEquals("비밀번호를 잊으셨나요?", suggestions.get("ko"));
        assertEquals("パスワードをお忘れですか？", suggestions.get("ja"));
    }

    @Test
    void parseSuggestions_doesNotExposeInvalidRawResponse() {
        String raw = "Thinking Process: " + "analysis ".repeat(100);

        RuntimeException error = assertThrows(
            RuntimeException.class,
            () -> TranslationSuggestionService.parseSuggestions(
                raw,
                List.of("ko"),
                Map.of("en", "Forgot password?")
            )
        );

        assertTrue(error.getMessage().contains("대상 언어"));
        assertFalse(error.getMessage().contains("Thinking Process"));
        assertFalse(error.getMessage().contains("analysis"));
    }

    @Test
    void parseSuggestions_selectsEarlierTranslationOverTrailingMetadataObject() {
        String raw = """
            {"ja": "保存"}
            Metadata: {"request_id": "abc-123"}
            """;

        Map<String, String> suggestions = TranslationSuggestionService.parseSuggestions(
            raw,
            List.of("ja"),
            Map.of("ko", "저장")
        );

        assertEquals("保存", suggestions.get("ja"));
    }

    @Test
    void parseSuggestions_rejectsNonStringLocaleValue() {
        RuntimeException error = assertThrows(
            RuntimeException.class,
            () -> TranslationSuggestionService.parseSuggestions(
                "{\"ja\": {\"text\": \"保存\"}}",
                List.of("ja"),
                Map.of("ko", "저장")
            )
        );

        assertTrue(error.getMessage().contains("문자열 번역"));
    }

    @Test
    void findMissingPlaceholders_detectsMissing() {
        String ref = "환영합니다, {0}님! 남은 포인트: %d점";
        String suggestedWithMissing = "ようこそ、{0}様！"; // %d is missing

        List<String> missing = TranslationSuggestionService.findMissingPlaceholders(ref, suggestedWithMissing);
        assertEquals(1, missing.size());
        assertEquals("%d", missing.get(0));

        String suggestedComplete = "ようこそ、{0}様！残りポイント: %d点";
        List<String> missingComplete = TranslationSuggestionService.findMissingPlaceholders(ref, suggestedComplete);
        assertTrue(missingComplete.isEmpty());
    }

    @Test
    void extractPlaceholders_extractsVariedFormats() {
        String text = "Hello {name}, you have {0} messages and %s status <br/>";
        List<String> placeholders = TranslationSuggestionService.extractPlaceholders(text);

        assertTrue(placeholders.contains("{name}"));
        assertTrue(placeholders.contains("{0}"));
        assertTrue(placeholders.contains("%s"));
        assertTrue(placeholders.contains("<br/>"));
    }
}
