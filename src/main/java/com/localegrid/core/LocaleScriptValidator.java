package com.localegrid.core;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UCharacterCategory;
import com.ibm.icu.lang.UScript;

import java.lang.Character.UnicodeScript;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates text with Unicode-script regular expressions derived from a
 * {@link LocaleScriptPolicy}.
 */
public final class LocaleScriptValidator {
    private static final int SUMMARY_CHARACTER_LIMIT = 5;
    private static final String UNIVERSALLY_ALLOWED_REGEX = "\\p{N}\\x{1F000}-\\x{1FAFF}";

    private final LocaleScriptPolicy policy;
    private final ConcurrentMap<Set<UnicodeScript>, Pattern> patternCache = new ConcurrentHashMap<>();

    public LocaleScriptValidator() {
        this(LocaleScriptPolicy.defaults());
    }

    public LocaleScriptValidator(LocaleScriptPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Validates text for a locale. Unsupported or malformed locale identifiers
     * are returned as unchecked and do not produce violations.
     */
    public ValidationResult validate(String locale, String text) {
        return policy.findRule(locale)
            .map(rule -> validate(rule, locale, text == null ? "" : text))
            .orElseGet(() -> ValidationResult.unchecked(locale));
    }

    private ValidationResult validate(LocaleScriptPolicy.Rule rule, String locale, String text) {
        Pattern disallowedPattern = patternCache.computeIfAbsent(
            rule.getAllowedScripts(),
            LocaleScriptValidator::compileDisallowedPattern
        );
        Matcher matcher = disallowedPattern.matcher(text);
        List<Violation> violations = new ArrayList<>();
        while (matcher.find()) {
            String character = matcher.group();
            int codePoint = character.codePointAt(0);
            UnicodeScript runtimeScript = UnicodeScript.of(codePoint);
            if (runtimeScript == UnicodeScript.UNKNOWN) {
                if (isIcuNumber(codePoint) || rule.allowsIcuScript(UScript.getScript(codePoint))) {
                    continue;
                }
            }
            violations.add(new Violation(character, codePoint, matcher.start(), runtimeScript));
        }
        return ValidationResult.checked(locale, rule, violations);
    }

    private static boolean isIcuNumber(int codePoint) {
        int characterType = UCharacter.getType(codePoint);
        return characterType == UCharacterCategory.DECIMAL_DIGIT_NUMBER
            || characterType == UCharacterCategory.LETTER_NUMBER
            || characterType == UCharacterCategory.OTHER_NUMBER;
    }

    private static Pattern compileDisallowedPattern(Set<UnicodeScript> allowedScripts) {
        StringBuilder regex = new StringBuilder("[^").append(UNIVERSALLY_ALLOWED_REGEX);
        for (UnicodeScript script : allowedScripts) {
            regex.append("\\p{sc=").append(script.name()).append('}');
        }
        regex.append(']');
        return Pattern.compile(regex.toString());
    }

    int cachedPatternCount() {
        return patternCache.size();
    }

    public static final class Violation {
        private final String character;
        private final int codePoint;
        private final int utf16Index;
        private final UnicodeScript script;

        private Violation(String character, int codePoint, int utf16Index, UnicodeScript script) {
            this.character = character;
            this.codePoint = codePoint;
            this.utf16Index = utf16Index;
            this.script = script;
        }

        public String getCharacter() {
            return character;
        }

        public int getCodePoint() {
            return codePoint;
        }

        public int getUtf16Index() {
            return utf16Index;
        }

        public UnicodeScript getScript() {
            return script;
        }
    }

    public static final class ValidationResult {
        private final String locale;
        private final LocaleScriptPolicy.Rule rule;
        private final List<Violation> violations;

        private ValidationResult(String locale, LocaleScriptPolicy.Rule rule, List<Violation> violations) {
            this.locale = locale;
            this.rule = rule;
            this.violations = Collections.unmodifiableList(new ArrayList<>(violations));
        }

        private static ValidationResult unchecked(String locale) {
            return new ValidationResult(locale, null, List.of());
        }

        private static ValidationResult checked(
            String locale,
            LocaleScriptPolicy.Rule rule,
            List<Violation> violations
        ) {
            return new ValidationResult(locale, rule, violations);
        }

        public String getLocale() {
            return locale;
        }

        public LocaleScriptPolicy.Rule getRule() {
            return rule;
        }

        public boolean isChecked() {
            return rule != null;
        }

        public boolean isValid() {
            return violations.isEmpty();
        }

        public List<Violation> getViolations() {
            return violations;
        }

        public List<String> getUnexpectedCharacters() {
            LinkedHashSet<String> characters = new LinkedHashSet<>();
            for (Violation violation : violations) {
                characters.add(violation.getCharacter());
            }
            return List.copyOf(characters);
        }

        public Set<UnicodeScript> getUnexpectedScripts() {
            LinkedHashSet<UnicodeScript> scripts = new LinkedHashSet<>();
            for (Violation violation : violations) {
                scripts.add(violation.getScript());
            }
            return Collections.unmodifiableSet(scripts);
        }

        public String summarize() {
            if (violations.isEmpty()) {
                return "";
            }

            List<String> unexpectedCharacters = getUnexpectedCharacters();
            List<String> displayedCharacters = unexpectedCharacters.subList(
                0,
                Math.min(SUMMARY_CHARACTER_LIMIT, unexpectedCharacters.size())
            );
            String remainingCharacters = unexpectedCharacters.size() > SUMMARY_CHARACTER_LIMIT
                ? " 외 " + (unexpectedCharacters.size() - SUMMARY_CHARACTER_LIMIT) + "개"
                : "";
            return "허용되지 않은 문자: " + String.join(", ", displayedCharacters) + remainingCharacters
                + " (문자 체계: " + joinScriptNames(violations) + ")";
        }

        private static String joinScriptNames(List<Violation> violations) {
            Set<String> names = new LinkedHashSet<>();
            for (Violation violation : violations) {
                UnicodeScript runtimeScript = violation.getScript();
                if (runtimeScript == UnicodeScript.UNKNOWN) {
                    int icuScript = UScript.getScript(violation.getCodePoint());
                    if (icuScript != UScript.UNKNOWN) {
                        names.add(UScript.getName(icuScript));
                        continue;
                    }
                }
                names.add(scriptDisplayName(runtimeScript));
            }
            return String.join(", ", names);
        }

        private static String scriptDisplayName(UnicodeScript script) {
            return switch (script) {
                case HANGUL -> "한글";
                case LATIN -> "라틴";
                case HIRAGANA -> "히라가나";
                case KATAKANA -> "가타카나";
                case HAN -> "한자";
                case CYRILLIC -> "키릴";
                case ARABIC -> "아랍";
                case DEVANAGARI -> "데바나가리";
                case THAI -> "타이";
                case LAO -> "라오";
                case GREEK -> "그리스";
                case HEBREW -> "히브리";
                default -> script.name();
            };
        }
    }
}
