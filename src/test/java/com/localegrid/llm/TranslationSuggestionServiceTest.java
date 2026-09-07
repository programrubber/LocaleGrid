package com.localegrid.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TranslationSuggestionServiceTest {
    @Test
    void multilingualSetsKeepIdsAndSharedWordingAndRejectIncompleteSets() {
        var sets = TranslationSuggestionService.parseSuggestionSets("""
            {"candidates":[
              {"id":"A","translations":{"ko":"저장 A","en":"Save"}},
              {"id":"B","translations":{"ko":"저장 B"}},
              {"id":"C","translations":{"ko":"저장 C","en":"Save","de":"ignore"}},
              {"id":"C","translations":{"ko":"duplicate ID","en":"Duplicate"}},
              {"id":"D","translations":{"ko":"extra","en":"Extra"}}
            ]}
            """, List.of("ko", "en"));
        assertEquals(List.of("A", "C"), sets.stream().map(TranslationSuggestionSet::id).toList());
        assertEquals("Save", sets.get(0).translations().get("en"));
        assertEquals("Save", sets.get(1).translations().get("en"));
        assertFalse(sets.get(1).translations().containsKey("de"));
        assertThrows(RuntimeException.class, () -> TranslationSuggestionService.parseSuggestionSets(
            "{\"ko\":[\"A\",\"B\"],\"en\":[\"A\",\"B\"]}", List.of("ko", "en")));
        assertThrows(RuntimeException.class, () -> TranslationSuggestionService.parseSuggestionSets(
            "{\"candidates\":[{\"id\":\"A\",\"translations\":{\"ko\":\"혼자\"}}]}", List.of("ko", "en")));
        var legacy = TranslationSuggestionService.parseSuggestionSets(
            "{\"translations\":{\"ko\":\"저장\",\"zh_CN\":\"保存\"}}", List.of("ko", "zh-CN"));
        assertEquals("A", legacy.get(0).id());
        assertEquals("保存", legacy.get(0).translations().get("zh-CN"));
    }

    @Test
    void candidateArraysAreFilteredDeduplicatedAndLimitedWithoutPadding() {
        var result = TranslationSuggestionService.parseSuggestions("""
            {"ko":[" 로그인 실패 ","로그인 실패","",null,7,{"text":"skip"},
                    "로그인하지 못했습니다.","로그인에 실패했습니다.","fourth"],
             "en":"Login failed.","ja":[false," ","ログイン失敗"],"de":["ignore"]}
            """, List.of("ko", "en", "ja"), Map.of());
        assertEquals(List.of("로그인 실패", "로그인하지 못했습니다.", "로그인에 실패했습니다."), result.get("ko"));
        assertEquals(List.of("Login failed."), result.get("en"));
        assertEquals(List.of("ログイン失敗"), result.get("ja"));
        assertFalse(result.containsKey("de"));
        var wrapped = TranslationSuggestionService.parseSuggestions(
            "{\"translations\":{\"zh_CN\":[\"保存\",\"储存\"]}}", List.of("zh-CN"), Map.of());
        assertEquals(List.of("保存", "储存"), wrapped.get("zh-CN"));
        assertThrows(RuntimeException.class, () -> TranslationSuggestionService.parseSuggestions(
            "{\"ja\":[null, false, 5, \"\"]}", List.of("ja"), Map.of()));
    }

    @Test
    void writingBriefDraftsCopyWhileExistingValuesKeepTranslationBehavior() {
        java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> sentSystem = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> sentUser = new java.util.concurrent.atomic.AtomicReference<>();
        LocaleGridLlmClient client = new LocaleGridLlmClient() {
            @Override public java.util.concurrent.CompletableFuture<String> sendChatCompletion(
                String endpoint, String model, String apiKey, String system, String user, double temperature, int timeout
            ) {
                requests.incrementAndGet();
                sentSystem.set(system);
                sentUser.set(user);
                return java.util.concurrent.CompletableFuture.completedFuture(
                    "{\"ko\":\"로그인에 실패했습니다.\",\"en\":\"Login failed.\",\"ja\":\"ログインに失敗しました。\"}");
            }
        };
        var service = new TranslationSuggestionService(client);
        var settings = new com.localegrid.settings.LocaleGridAiSettingsState();
        String brief = "로그인을 1~3회 실패하였을 때 출력할 만한 문구";
        for (TranslationStyle style : TranslationStyle.values()) {
            var result = service.requestSuggestions("login", Map.of(), List.of("ko", "en", "ja"),
                settings, style, brief).join();
            assertEquals(3, result.size());
            assertEquals(List.of("로그인에 실패했습니다."), result.get("ko"));
            assertTrue(sentSystem.get().contains("TASK: DRAFT UI COPY"));
            assertTrue(sentSystem.get().contains("array of 1-3"));
            assertTrue(sentSystem.get().contains("Do not pad to three"));
            assertTrue(sentSystem.get().contains("not text to translate literally"));
            assertTrue(sentSystem.get().contains("remaining attempts"));
            assertTrue(sentSystem.get().contains("Do not infer TCP reset"));
            assertTrue(sentUser.get().contains(brief));
            if (style == TranslationStyle.PRESERVE) {
                assertTrue(sentSystem.get().contains("Infer the intended UI role"));
                assertFalse(sentSystem.get().contains("Preserve the source's form"));
            } else assertTrue(sentSystem.get().contains(style.instruction()));
        }
        assertThrows(java.util.concurrent.CompletionException.class, () -> service.requestSuggestions(
            "login", Map.of(), List.of("en"), settings, TranslationStyle.LABEL, "  ").join());
        assertEquals(3, requests.get());
        service.requestSuggestions("login", Map.of("ko", "로그인에 실패했습니다."), List.of("en"),
            settings, TranslationStyle.SENTENCE, brief).join();
        assertFalse(sentSystem.get().contains("TASK: DRAFT UI COPY"));
        assertFalse(sentUser.get().contains(brief));
        assertTrue(sentUser.get().contains("ko: 로그인에 실패했습니다."));
        assertTrue(TranslationSuggestionService.buildUserPrompt("login", Map.of(), List.of("ko"),
            "사용자 {name} 로그인 실패 안내").contains("{name}"));
    }

    @Test
    void selectedStyleReachesClientAndRetainsTranslationContract() {
        java.util.concurrent.atomic.AtomicReference<String> sent = new java.util.concurrent.atomic.AtomicReference<>();
        LocaleGridLlmClient client = new LocaleGridLlmClient() {
            @Override public java.util.concurrent.CompletableFuture<String> sendChatCompletion(
                String endpoint, String model, String apiKey, String system, String user, double temperature, int timeout
            ) {
                sent.set(system);
                assertTrue(user.contains("{name}"));
                return java.util.concurrent.CompletableFuture.completedFuture("{\"ja\":\"保存 {name}\"}");
            }
        };
        TranslationSuggestionService service = new TranslationSuggestionService(client);
        for (TranslationStyle style : TranslationStyle.values()) {
            var result = service.requestSuggestions("save", Map.of("ko", "사용자 {name}의 변경 사항 저장"),
                List.of("ja"), new com.localegrid.settings.LocaleGridAiSettingsState(), style).join();
            assertEquals(List.of("保存 {name}"), result.get("ja"));
            assertTrue(sent.get().contains(style.instruction()));
            assertTrue(sent.get().contains("network management web consoles"));
            assertTrue(sent.get().contains("Do not infer TCP reset"));
            for (TranslationStyle other : TranslationStyle.values()) {
                if (other != style) assertFalse(sent.get().contains(other.instruction()));
            }
            assertTrue(sent.get().contains("Preserve all placeholders"));
            assertTrue(sent.get().contains("ONLY a valid JSON object"));
            assertTrue(sent.get().contains("candidates array containing 1-3"));
            assertTrue(sent.get().contains("Do not pad to three"));
            assertFalse(sent.get().contains("concisely"));
        }
        assertEquals(TranslationSuggestionService.buildSystemPrompt(TranslationStyle.PRESERVE),
            TranslationSuggestionService.buildSystemPrompt());
        String label = TranslationSuggestionService.buildSystemPrompt(TranslationStyle.LABEL);
        assertTrue(label.contains("Labels may be as long as needed"));
        assertTrue(label.contains("Do not force every toggle label into an imperative"));
        assertTrue(label.contains("state values only when the source supplies the actual state"));
        assertTrue(label.contains("neutral property wording"));
        String sentence = TranslationSuggestionService.buildSystemPrompt(TranslationStyle.SENTENCE);
        assertTrue(sentence.contains("Do not add causes, behavior, risks, effects, actors, or success claims"));
        assertTrue(sentence.contains("A standalone Whether-clause is not a complete sentence"));
    }

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

        Map<String, List<String>> suggestions = TranslationSuggestionService.parseSuggestions(
            raw,
            List.of("ja", "vi"),
            Map.of("ko", "저장하기")
        );

        assertEquals(2, suggestions.size());
        assertEquals(List.of("保存する"), suggestions.get("ja"));
        assertEquals(List.of("Lưu thay đổi"), suggestions.get("vi"));
    }

    @Test
    void parseSuggestions_ignoresUnrequestedLocales() {
        String raw = "{\"ja\": \"はい\", \"de\": \"Ja\", \"fr\": \"Oui\"}";
        Map<String, List<String>> suggestions = TranslationSuggestionService.parseSuggestions(
            raw,
            List.of("ja"),
            Map.of("ko", "예")
        );

        assertEquals(1, suggestions.size());
        assertEquals(List.of("はい"), suggestions.get("ja"));
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

        Map<String, List<String>> suggestions = TranslationSuggestionService.parseSuggestions(
            raw,
            List.of("ko", "ja"),
            Map.of("en", "Forgot password?")
        );

        assertEquals(List.of("비밀번호를 잊으셨나요?"), suggestions.get("ko"));
        assertEquals(List.of("パスワードをお忘れですか？"), suggestions.get("ja"));
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

        Map<String, List<String>> suggestions = TranslationSuggestionService.parseSuggestions(
            raw,
            List.of("ja"),
            Map.of("ko", "저장")
        );

        assertEquals(List.of("保存"), suggestions.get("ja"));
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
