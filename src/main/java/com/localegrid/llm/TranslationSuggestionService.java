package com.localegrid.llm;

import com.localegrid.settings.LocaleGridSettingsState;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 다국어 키와 기존 입력된 언어들의 문장을 문맥(Context)으로 종합하여,
 * 비어 있거나 필요한 대상 언어의 번역 문구를 LLM에 질의하고 추천 결과를 생성하는 서비스.
 */
public class TranslationSuggestionService {
    private static final TranslationSuggestionService INSTANCE = new TranslationSuggestionService();

    private final LocaleGridLlmClient llmClient;

    public TranslationSuggestionService() {
        this(LocaleGridLlmClient.getInstance());
    }

    public TranslationSuggestionService(LocaleGridLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public static TranslationSuggestionService getInstance() {
        return INSTANCE;
    }

    /**
     * 특정 Row의 키와 기존 번역된 언어 문장들을 기반으로 대상 언어 번역 제안을 요청합니다.
     *
     * @param key                   다국어 키명 (예: common.button.save)
     * @param referenceTranslations 이미 입력되어 있는 참조 언어별 텍스트 (예: {"ko": "저장", "en": "Save"})
     * @param targetLocales         번역 제안을 받고자 하는 대상 언어 코드 목록 (예: ["ja", "vi"])
     * @param settings              LLM 연결 설정
     * @return 언어 코드별 제안 번역 문구 맵 (예: {"ja": "保存", "vi": "Lưu"})
     */
    public CompletableFuture<Map<String, String>> requestSuggestions(
        String key,
        Map<String, String> referenceTranslations,
        List<String> targetLocales,
        LocaleGridSettingsState settings
    ) {
        if (referenceTranslations == null || referenceTranslations.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("참조할 기존 언어 문장이 없습니다."));
        }
        if (targetLocales == null || targetLocales.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(key, referenceTranslations, targetLocales);

        return llmClient.sendChatCompletion(
            settings.getNormalizedLlmEndpoint(),
            settings.getNormalizedLlmModel(),
            settings.llmApiKey,
            systemPrompt,
            userPrompt,
            settings.llmTemperature,
            settings.llmTimeoutSeconds
        ).thenApply(rawResponse -> parseSuggestions(rawResponse, targetLocales, referenceTranslations));
    }

    public static String buildSystemPrompt() {
        return """
            You are an expert localization translation assistant for software internationalization (i18n).
            Translate software UI text accurately, naturally, and concisely into the requested target languages.

            CRITICAL RULES:
            1. Carefully analyze ALL provided reference translations together to resolve ambiguity and understand the exact UI context.
            2. Preserve all placeholders (e.g. {0}, {name}, %s, %d, HTML tags, and escape sequences like \\n) EXACTLY as they appear in references.
            3. Return ONLY a valid JSON object mapping the target locale codes to their translated text.
            4. Do NOT wrap the JSON in Markdown fences (```json) or add any extra explanations.

            Example output format:
            {"ja": "保存", "vi": "Lưu"}
            """.stripIndent().trim();
    }

    public static String buildUserPrompt(
        String key,
        Map<String, String> referenceTranslations,
        List<String> targetLocales
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Translation Key: ").append(key).append("\n\n");

        sb.append("Reference Translations (Context):\n");
        for (Map.Entry<String, String> entry : referenceTranslations.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        sb.append("\nTarget Languages to Translate into:\n");
        for (String target : targetLocales) {
            sb.append("- ").append(target).append("\n");
        }

        sb.append("\nGenerate a JSON object with translations for all target languages:");
        return sb.toString();
    }

    public static Map<String, String> parseSuggestions(
        String rawResponse,
        List<String> targetLocales,
        Map<String, String> referenceTranslations
    ) {
        List<String> candidates = LocaleGridLlmClient.extractJsonObjectCandidates(rawResponse);
        JSONObject json = null;
        for (int index = candidates.size() - 1; index >= 0; index--) {
            JSONObject candidate = unwrapTranslations(new JSONObject(candidates.get(index)));
            if (containsRequestedLocale(candidate, targetLocales)) {
                json = candidate;
                break;
            }
        }

        if (json == null) {
            throw new RuntimeException(
                "LLM 응답 JSON에 요청한 대상 언어의 문자열 번역이 없습니다."
            );
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (String target : targetLocales) {
            String value = findLocaleValueInJson(json, target);
            if (value != null && !value.isBlank()) {
                result.put(target, value.trim());
            }
        }

        if (result.isEmpty()) {
            throw new RuntimeException(
                "LLM 응답 JSON에 요청한 대상 언어의 문자열 번역이 없습니다."
            );
        }
        return result;
    }

    private static JSONObject unwrapTranslations(JSONObject json) {
        JSONObject wrappedTranslations = json.optJSONObject("translations");
        return wrappedTranslations == null ? json : wrappedTranslations;
    }

    private static boolean containsRequestedLocale(JSONObject json, List<String> targetLocales) {
        for (String target : targetLocales) {
            if (findLocaleValueInJson(json, target) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 대소문자나 언어 태그 형식 차이(예: zh-CN vs zh_CN vs zh-cn)를 고려하여 JSON에서 값을 검색합니다.
     */
    private static String findLocaleValueInJson(JSONObject json, String targetLocale) {
        if (json.has(targetLocale)) {
            return stringValue(json.opt(targetLocale));
        }
        for (String key : json.keySet()) {
            if (key.equalsIgnoreCase(targetLocale) || normalizeTag(key).equals(normalizeTag(targetLocale))) {
                return stringValue(json.opt(key));
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value instanceof String text ? text : null;
    }

    private static String normalizeTag(String tag) {
        return tag.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    /**
     * 참조 문장에 포함된 플레이스홀더({0}, {name}, %s 등)가 추천 문구에도 잘 유지되었는지 검증합니다.
     * 누락된 플레이스홀더가 있다면 누락된 목록을 반환합니다.
     */
    public static List<String> findMissingPlaceholders(String referenceText, String suggestedText) {
        if (referenceText == null || suggestedText == null) {
            return Collections.emptyList();
        }
        List<String> placeholders = extractPlaceholders(referenceText);
        List<String> missing = new ArrayList<>();
        for (String ph : placeholders) {
            if (!suggestedText.contains(ph)) {
                missing.add(ph);
            }
        }
        return missing;
    }

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
        "(\\{[a-zA-Z0-9_]+\\}|%[0-9]*\\$?[a-zA-Z]|%[sdf]|<[^>]+>)"
    );

    public static List<String> extractPlaceholders(String text) {
        List<String> list = new ArrayList<>();
        Matcher m = PLACEHOLDER_PATTERN.matcher(text);
        while (m.find()) {
            String match = m.group();
            if (!list.contains(match)) {
                list.add(match);
            }
        }
        return list;
    }
}
