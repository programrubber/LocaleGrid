package com.localegrid.llm;

import com.localegrid.settings.LocaleGridAiSettingsState;
import org.json.JSONObject;
import org.json.JSONArray;

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

    private static final String NETWORK_RULES = """
            NETWORK TERMINOLOGY:
            Distinguish configured enablement (Enabled/Disabled) from actual operation (Active/Inactive),
            link state (Up/Down), connection state, and synchronization state.
            Do not interchange Applied, Assigned, and Enabled, or conflate policy and rule names.
            Do not infer TCP reset, ICMP response, or silent discard from generic Deny/Block.
            Preserve vendor-defined actions, acronyms, protocol names, and terms when supplied.
            Do not silently replace SSL with TLS or change terms to another vendor's terminology.

        """;

    private static final String CANDIDATE_RULES = """

        CANDIDATES:
        Offer 1-3 multilingual candidate SETS, ordered with the best first and identified as A, B, C.
        Each set must contain every requested locale with the same intent, tone, and level of detail.
        A in Korean must correspond to A in English and Japanese; never generate independent locale lists.
        It is valid for two sets to share wording in one locale when that is the natural translation.
        Do not change or remove that locale's wording merely to make each locale independently unique.
        Every candidate must preserve the same meaning, facts, conditions, and selected style.
        Default to exactly ONE best candidate. A clear, standard wording is enough by itself.
        Generic failure notifications and standard action labels normally need only one candidate.
        Do not recast a generic failure as a denial, a processing error, a timeout, or an invalid credential.
        Those imply different facts and are not interchangeable alternatives.
        Add a second or third only when a genuinely useful, natural alternative exists for the same intent.
        Use fewer candidates when natural, meaningful alternatives are unavailable. Do not pad to three.
        Do not vary only casing, punctuation, or whitespace. Do not invent facts or weaken technical meaning for variety.
        """;

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
     * @return 언어 코드별 1~3개 제안 문구 목록
     */
    public CompletableFuture<Map<String, List<String>>> requestSuggestions(
        String key,
        Map<String, String> referenceTranslations,
        List<String> targetLocales,
        LocaleGridAiSettingsState settings
    ) {
        return requestSuggestions(key, referenceTranslations, targetLocales, settings, TranslationStyle.PRESERVE);
    }

    public CompletableFuture<Map<String, List<String>>> requestSuggestions(
        String key,
        Map<String, String> referenceTranslations,
        List<String> targetLocales,
        LocaleGridAiSettingsState settings,
        TranslationStyle style
    ) {
        return requestSuggestions(key, referenceTranslations, targetLocales, settings, style, "");
    }

    /** Drafts localized UI copy from a brief only when the row has no reference translations. */
    public CompletableFuture<Map<String, List<String>>> requestSuggestions(
        String key, Map<String, String> referenceTranslations, List<String> targetLocales,
        LocaleGridAiSettingsState settings, TranslationStyle style, String writingBrief
    ) {
        return requestSuggestionSets(key, referenceTranslations, targetLocales, settings, style, writingBrief)
            .thenApply(sets -> {
                Map<String, List<String>> result = new LinkedHashMap<>();
                for (TranslationSuggestionSet set : sets) set.translations().forEach((locale, text) ->
                    result.computeIfAbsent(locale, ignored -> new ArrayList<>()).add(text));
                return result;
            });
    }

    public CompletableFuture<List<TranslationSuggestionSet>> requestSuggestionSets(
        String key, Map<String, String> referenceTranslations, List<String> targetLocales,
        LocaleGridAiSettingsState settings, TranslationStyle style, String writingBrief
    ) {
        Map<String, String> references = referenceTranslations == null ? Collections.emptyMap() : referenceTranslations;
        String source = references.isEmpty() && writingBrief != null ? writingBrief.strip() : "";
        if (references.isEmpty() && source.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("어떤 상황에 필요한 문구인지 입력하세요."));
        }
        if (targetLocales == null || targetLocales.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        String systemPrompt = source.isEmpty() ? buildSystemPrompt(style) : buildDraftSystemPrompt(style);
        String userPrompt = buildUserPrompt(key, references, targetLocales, source);

        return llmClient.sendChatCompletion(
            settings.getNormalizedLlmEndpoint(),
            settings.getNormalizedLlmModel(),
            settings.llmApiKey,
            systemPrompt,
            userPrompt,
            settings.llmTemperature,
            settings.llmTimeoutSeconds
        ).thenApply(rawResponse -> parseSuggestionSets(rawResponse, targetLocales));
    }

    public static String buildSystemPrompt() {
        return buildSystemPrompt(TranslationStyle.PRESERVE);
    }

    public static String buildSystemPrompt(TranslationStyle style) {
        Objects.requireNonNull(style, "style");
        return """
            You are an expert localization translation assistant for network management web consoles.
            Translate UI text accurately and naturally into the requested target languages.
            The domain includes routing, switching, interfaces, VPN, wireless, monitoring, logging,
            and network security. Do not reinterpret ordinary account or UI features as firewall features.

            MEANING AND CONTEXT:
            Preserve meaning, polarity, conditions, scope, direction, technical behavior, identifiers,
            numbers, units, protocol names, and product terms. Accuracy takes priority over style and brevity.
            Do not impose a length limit or add facts absent from the references.
            Use explicit UI context and source text first. Use the translation key and other references
            as supporting evidence, not as permission to invent a UI role or combine conflicting meanings.
            If the UI role is unknown and the source names a property, prefer neutral property wording
            rather than inventing a toggle action, an observed state, or a confirmation question.
            Preserve questions, warnings, and events already expressed by the source.

            """ + NETWORK_RULES + """
            CRITICAL RULES:
            1. Carefully analyze ALL provided reference translations together to resolve ambiguity and understand the exact UI context.
            2. Preserve all placeholders (e.g. {0}, {name}, %s, %d, HTML tags, and escape sequences like \\n) EXACTLY as they appear in references.
            3. Return ONLY a valid JSON object with a candidates array containing 1-3 multilingual sets; each set has id and translations.
            4. Do NOT wrap the JSON in Markdown fences (```json) or add any extra explanations.

            Example output format:
            {"candidates": [{"id": "A", "translations": {"ja": "保存", "vi": "Lưu"}}]}
            """.stripIndent().trim()
            + "\n\nOUTPUT STYLE:\n" + style.instruction() + CANDIDATE_RULES
            + "\n\nBefore returning, check semantic role, technical meaning, selected style, terminology, casing,"
            + " and exact preservation of placeholders and identifiers."
            + " Return only the candidates JSON object, without explanations or review notes.";
    }

    public static String buildDraftSystemPrompt(TranslationStyle style) {
        Objects.requireNonNull(style, "style");
        String outputStyle = switch (style) {
            case PRESERVE -> """
                Infer the intended UI role from the brief and write natural copy for that role.
                For a failure notification, write a user-facing complete sentence in a polite UI tone,
                not a label describing the request. For a button or column, use appropriate label wording.
                """;
            case LABEL -> style.instruction() + """

                This is LABEL mode: output label wording even if the brief asks for a notification.
                For an event or failure, use a noun phrase naming it, not a full notification sentence.
                Korean example: 연결 실패. English example: Connection Failure. Japanese example: 接続失敗.
                Do not use Korean sentence endings or Japanese ました/です in such labels.
                """;
            case SENTENCE -> style.instruction() + """

                This is SENTENCE mode: output complete user-facing sentences, not noun phrases.
                Korean requires a finite predicate and appropriate polite ending, e.g. 연결에 실패했습니다.
                Japanese requires a complete predicate, e.g. 接続に失敗しました。
                English example: The connection failed. Use appropriate sentence punctuation in each locale.
                These are grammar examples, not additional facts or text to copy for unrelated situations.
                """;
        };
        return """
            You write localized UI copy for network management web consoles.
            The domain includes routing, switching, interfaces, VPN, wireless, monitoring, logging,
            and network security. Do not reinterpret ordinary account or UI features as firewall features.

            TASK: DRAFT UI COPY FROM A WRITING BRIEF.
            The brief describes a situation and the kind of text the user needs. It is not text to translate literally.
            Produce 1-3 multilingual sets of ready-to-use UI messages or labels, with equivalent meaning across languages within each set.
            Do not output a description such as "a message to display when login fails" or instructions to the developer.
            If the brief supplies exact wording to use, preserve that wording's meaning and placeholders.
            Apply the selected style to the resulting UI copy, not to the grammar of the brief itself.
            Source/reference in the style rules means the requested content and explicit facts in the brief.

            MEANING AND CONSTRAINTS:
            Distinguish the display condition from text intended for the user. A trigger such as "after 1-3 failed
            login attempts" does not identify the current attempt count. Do not include a trigger-only range
            in the resulting message unless the user explicitly asks to display that range.
            For example, a notification for 1-3 failed login attempts should say "Login failed.",
            not "Login failed 1-3 times." The range selects when to show the message, not what the message says.
            Preserve explicit conditions, negation, scope, identifiers, units, and technical meaning.
            Do not invent a failure cause, lockout policy, lockout threshold, remaining attempts, wait time,
            permissions, recovery action, or system behavior not supplied by the brief.
            Do not turn "1-3 failed attempts" into "3 attempts remaining" or "the account is locked after 3 attempts".
            When details are missing, use neutral copy valid for the stated situation rather than inventing facts.
            Use the key only as supporting context. Preserve all supplied placeholders such as {name}, {0}, %s,
            HTML tags, and escape sequences exactly. Do not invent placeholders for an unknown attempt count.
            """ + NETWORK_RULES + "\nOUTPUT STYLE:\n" + outputStyle + CANDIDATE_RULES + """

            OUTPUT CONTRACT:
            Return ONLY a valid JSON object with a candidates array of 1-3 multilingual sets.
            Example: {"candidates":[{"id":"A","translations":{"ko":"저장","en":"Save"}}]}
            Use IDs A, B, C and include every requested locale in each translations object.
            Include the language of the brief if it is a requested locale.
            Do not include Markdown fences, explanations, or review notes.
            Before returning, check the intended UI role, selected style, semantic equivalence, technical facts,
            casing, and exact preservation of placeholders. Return the usable UI copy only.
            """;
    }

    public static String buildUserPrompt(
        String key,
        Map<String, String> referenceTranslations,
        List<String> targetLocales
    ) {
        return buildUserPrompt(key, referenceTranslations, targetLocales, "");
    }

    public static String buildUserPrompt(
        String key, Map<String, String> referenceTranslations, List<String> targetLocales, String writingBrief
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Translation Key: ").append(key).append("\n\n");

        if (referenceTranslations.isEmpty() && writingBrief != null && !writingBrief.isBlank()) {
            sb.append("UI writing brief (JSON string):\n")
                .append(JSONObject.quote(writingBrief.strip())).append("\n")
                .append("Draft the UI copy requested by this brief for every target locale. Do not translate the brief literally.\n\n");
        }
        sb.append("Reference Translations (Context):\n");
        for (Map.Entry<String, String> entry : referenceTranslations.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        sb.append("\nTarget Languages to Translate into:\n");
        for (String target : targetLocales) {
            sb.append("- ").append(target).append("\n");
        }

        sb.append("\nGenerate {\"candidates\":[{\"id\":\"A\",\"translations\":{...}}]}. Each set must include all target locales. Default to one best set; include B and C only for useful equivalent alternatives:");
        return sb.toString();
    }

    public static List<TranslationSuggestionSet> parseSuggestionSets(String rawResponse, List<String> targetLocales) {
        List<String> objects = LocaleGridLlmClient.extractJsonObjectCandidates(rawResponse);
        for (int i = objects.size() - 1; i >= 0; i--) {
            JSONObject root = new JSONObject(objects.get(i));
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates == null) {
                // A legacy single translation per locale can safely form one set. Independent arrays cannot.
                Map<String, String> translations = completeTranslations(unwrapTranslations(root), targetLocales);
                if (!translations.isEmpty()) return List.of(new TranslationSuggestionSet("A", translations));
                continue;
            }
            Map<String, TranslationSuggestionSet> valid = new LinkedHashMap<>();
            for (int j = 0; j < candidates.length(); j++) {
                JSONObject candidate = candidates.optJSONObject(j);
                if (candidate == null) continue;
                String id = candidate.optString("id", "");
                if (!List.of("A", "B", "C").contains(id) || valid.containsKey(id)) continue;
                JSONObject values = candidate.optJSONObject("translations");
                if (values == null) continue;
                Map<String, String> translations = completeTranslations(values, targetLocales);
                if (translations.isEmpty() || valid.values().stream().anyMatch(set -> set.translations().equals(translations))) continue;
                valid.put(id, new TranslationSuggestionSet(id, translations));
            }
            if (!valid.isEmpty()) return valid.values().stream()
                .sorted(Comparator.comparing(TranslationSuggestionSet::id)).toList();
        }
        throw new RuntimeException("모든 대상 언어를 포함한 번역 제안 세트가 없습니다. 다시 제안해 주세요.");
    }

    private static Map<String, String> completeTranslations(JSONObject values, List<String> targets) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String target : targets) {
            Object value = values.opt(target);
            if (!values.has(target)) {
                for (String key : values.keySet()) if (normalizeTag(key).equals(normalizeTag(target))) {
                    value = values.opt(key);
                    break;
                }
            }
            if (!(value instanceof String text) || text.isBlank()) return Map.of();
            result.put(target, text.strip());
        }
        return result;
    }

    public static Map<String, List<String>> parseSuggestions(
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

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String target : targetLocales) {
            List<String> values = findLocaleValuesInJson(json, target);
            if (!values.isEmpty()) result.put(target, values);
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
            if (!findLocaleValuesInJson(json, target).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 대소문자나 언어 태그 형식 차이(예: zh-CN vs zh_CN vs zh-cn)를 고려하여 JSON에서 값을 검색합니다.
     */
    private static List<String> findLocaleValuesInJson(JSONObject json, String targetLocale) {
        if (json.has(targetLocale)) return candidateValues(json.opt(targetLocale));
        for (String key : json.keySet()) {
            if (key.equalsIgnoreCase(targetLocale) || normalizeTag(key).equals(normalizeTag(targetLocale))) {
                return candidateValues(json.opt(key));
            }
        }
        return List.of();
    }

    private static List<String> candidateValues(Object value) {
        // Accept legacy single-string responses from models that ignore the array format.
        JSONArray array = value instanceof JSONArray values ? values : new JSONArray().put(value);
        Set<String> distinct = new LinkedHashSet<>();
        for (int i = 0; i < array.length() && distinct.size() < 3; i++) {
            if (array.opt(i) instanceof String text && !text.isBlank()) distinct.add(text.strip());
        }
        return List.copyOf(distinct);
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
