package com.localegrid.core;

import com.ibm.icu.lang.UScript;
import com.ibm.icu.util.ULocale;

import java.lang.Character.UnicodeScript;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IllformedLocaleException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Defines the Unicode scripts that are expected for a locale.
 *
 * <p>Locale identifiers are matched from the most specific identifier to the
 * least specific one. For example, {@code sr-Latn-RS} tries {@code sr-latn-rs},
 * {@code sr-latn}, then {@code sr}. Underscores and hyphens are treated equally.
 * Common punctuation/symbols and inherited combining characters are always
 * allowed. Explicit script subtags are always respected,
 * while other well-formed locales use CLDR likely-subtags as a fallback.</p>
 */
public final class LocaleScriptPolicy {
    private static final LocaleScriptPolicy DEFAULTS = builder()
        .allow("ko", UnicodeScript.HANGUL, UnicodeScript.LATIN)
        .allow("en", UnicodeScript.LATIN)
        .allow("ja", UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA, UnicodeScript.HAN, UnicodeScript.LATIN)
        .allow("vi", UnicodeScript.LATIN)
        .build();

    private final Map<String, Rule> rules;
    private final ConcurrentMap<String, Optional<Rule>> resolvedRuleCache = new ConcurrentHashMap<>();

    private LocaleScriptPolicy(Map<String, Rule> rules) {
        this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
    }

    public static LocaleScriptPolicy defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(rules);
    }

    /**
     * Finds the most specific rule available for the supplied locale.
     */
    public Optional<Rule> findRule(String locale) {
        if (locale == null) {
            return Optional.empty();
        }
        String cacheKey = locale.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        return resolvedRuleCache.computeIfAbsent(cacheKey, ignored -> findRuleUncached(locale));
    }

    private Optional<Rule> findRuleUncached(String locale) {
        ParsedLocale parsedLocale = parseLocale(locale);
        if (parsedLocale == null) {
            return Optional.empty();
        }

        String candidate = parsedLocale.normalizedTag;
        while (candidate != null && !candidate.equals(parsedLocale.language)) {
            Rule rule = rules.get(candidate);
            if (rule != null) {
                return Optional.of(rule);
            }

            int separator = candidate.lastIndexOf('-');
            candidate = separator >= 0 ? candidate.substring(0, separator) : null;
        }

        Rule languageRule = rules.get(parsedLocale.language);
        if (languageRule != null) {
            if (!parsedLocale.script.isEmpty()) {
                return scriptsForSubtag(parsedLocale.script)
                    .map(scripts -> new Rule(
                        parsedLocale.language + "-" + parsedLocale.script.toLowerCase(Locale.ROOT),
                        scripts
                    ));
            }
            return Optional.of(languageRule);
        }
        return findLikelySubtagRule(parsedLocale);
    }

    public boolean supports(String locale) {
        return findRule(locale).isPresent();
    }

    private static String normalizeLocale(String locale) {
        ParsedLocale parsedLocale = parseLocale(locale);
        return parsedLocale == null ? null : parsedLocale.normalizedTag;
    }

    private static ParsedLocale parseLocale(String locale) {
        if (locale == null) {
            return null;
        }

        String compatibleTag = locale.trim().replace('_', '-');
        if (compatibleTag.isEmpty()) {
            return null;
        }

        try {
            Locale parsed = new Locale.Builder().setLanguageTag(compatibleTag).build();
            String language = parsed.getLanguage().toLowerCase(Locale.ROOT);
            if (language.isEmpty() && !parsed.getScript().isEmpty()) {
                language = "und";
            }
            if (language.isEmpty()) {
                return null;
            }
            return new ParsedLocale(
                parsed.toLanguageTag().toLowerCase(Locale.ROOT),
                language,
                parsed.getScript()
            );
        } catch (IllformedLocaleException exception) {
            return null;
        }
    }

    private static Optional<ScriptSelection> scriptsForSubtag(String scriptSubtag) {
        EnumSet<UnicodeScript> scripts = EnumSet.noneOf(UnicodeScript.class);
        Set<Integer> icuScripts = new LinkedHashSet<>();
        switch (scriptSubtag.toLowerCase(Locale.ROOT)) {
            case "latn" -> scripts.add(UnicodeScript.LATIN);
            case "jpan" -> {
                scripts.add(UnicodeScript.HIRAGANA);
                scripts.add(UnicodeScript.KATAKANA);
                scripts.add(UnicodeScript.HAN);
            }
            case "kore" -> scripts.add(UnicodeScript.HANGUL);
            case "hans", "hant", "hani" -> scripts.add(UnicodeScript.HAN);
            case "hanb" -> {
                scripts.add(UnicodeScript.HAN);
                scripts.add(UnicodeScript.BOPOMOFO);
            }
            case "hrkt" -> {
                scripts.add(UnicodeScript.HIRAGANA);
                scripts.add(UnicodeScript.KATAKANA);
            }
            case "latf", "latg" -> scripts.add(UnicodeScript.LATIN);
            case "cyrs" -> scripts.add(UnicodeScript.CYRILLIC);
            case "syre", "syrj", "syrn" -> scripts.add(UnicodeScript.SYRIAC);
            case "aran" -> scripts.add(UnicodeScript.ARABIC);
            case "geok" -> scripts.add(UnicodeScript.GEORGIAN);
            default -> {
                try {
                    UnicodeScript resolvedScript = UnicodeScript.forName(scriptSubtag);
                    if (resolvedScript == UnicodeScript.UNKNOWN) {
                        return Optional.empty();
                    }
                    scripts.add(resolvedScript);
                } catch (IllegalArgumentException exception) {
                    int icuScript = UScript.getCodeFromName(scriptSubtag);
                    if (icuScript < 0 || icuScript == UScript.UNKNOWN) {
                        return Optional.empty();
                    }
                    icuScripts.add(icuScript);
                }
            }
        }
        scripts.add(UnicodeScript.LATIN);
        addIcuScripts(icuScripts, scripts);
        return Optional.of(new ScriptSelection(scripts, icuScripts));
    }

    private static Optional<Rule> findLikelySubtagRule(ParsedLocale parsedLocale) {
        ULocale locale = ULocale.forLanguageTag(parsedLocale.normalizedTag);
        String likelyScript = ULocale.addLikelySubtags(locale).getScript();
        if (likelyScript == null || likelyScript.isBlank()) {
            return Optional.empty();
        }
        return scriptsForSubtag(likelyScript)
            .map(scripts -> new Rule(parsedLocale.normalizedTag, scripts));
    }

    private record ParsedLocale(String normalizedTag, String language, String script) {
    }

    private record ScriptSelection(Set<UnicodeScript> runtimeScripts, Set<Integer> icuScripts) {
        private static ScriptSelection fromRuntimeScripts(Set<UnicodeScript> scripts) {
            Set<Integer> icuScripts = new LinkedHashSet<>();
            addIcuScripts(icuScripts, scripts);
            return new ScriptSelection(scripts, icuScripts);
        }
    }

    private static void addIcuScripts(Set<Integer> target, Set<UnicodeScript> scripts) {
        for (UnicodeScript script : scripts) {
            int icuScript = UScript.getCodeFromName(script.name());
            if (icuScript >= 0) {
                target.add(icuScript);
            }
        }
    }

    public static final class Rule {
        private final String locale;
        private final Set<UnicodeScript> allowedScripts;
        private final Set<Integer> allowedIcuScripts;

        private Rule(String locale, Set<UnicodeScript> allowedScripts) {
            this(locale, ScriptSelection.fromRuntimeScripts(allowedScripts));
        }

        private Rule(String locale, ScriptSelection scriptSelection) {
            this.locale = locale;
            EnumSet<UnicodeScript> effectiveScripts = EnumSet.copyOf(scriptSelection.runtimeScripts());
            effectiveScripts.add(UnicodeScript.COMMON);
            effectiveScripts.add(UnicodeScript.INHERITED);
            this.allowedScripts = Collections.unmodifiableSet(effectiveScripts);

            Set<Integer> effectiveIcuScripts = new LinkedHashSet<>(scriptSelection.icuScripts());
            addIcuScripts(effectiveIcuScripts, effectiveScripts);
            this.allowedIcuScripts = Collections.unmodifiableSet(effectiveIcuScripts);
        }

        public String getLocale() {
            return locale;
        }

        public Set<UnicodeScript> getAllowedScripts() {
            return allowedScripts;
        }

        boolean allowsIcuScript(int script) {
            return allowedIcuScripts.contains(script);
        }
    }

    public static final class Builder {
        private final Map<String, Rule> rules;

        private Builder() {
            this.rules = new LinkedHashMap<>();
        }

        private Builder(Map<String, Rule> source) {
            this.rules = new LinkedHashMap<>(source);
        }

        public Builder allow(String locale, UnicodeScript firstScript, UnicodeScript... additionalScripts) {
            Objects.requireNonNull(firstScript, "firstScript");
            Objects.requireNonNull(additionalScripts, "additionalScripts");

            EnumSet<UnicodeScript> scripts = EnumSet.of(firstScript);
            for (UnicodeScript script : additionalScripts) {
                scripts.add(Objects.requireNonNull(script, "additionalScripts contains null"));
            }
            return allow(locale, scripts);
        }

        public Builder allow(String locale, Set<UnicodeScript> scripts) {
            String normalizedLocale = normalizeLocale(locale);
            if (normalizedLocale == null) {
                throw new IllegalArgumentException("Invalid locale identifier: " + locale);
            }
            Objects.requireNonNull(scripts, "scripts");
            if (scripts.isEmpty()) {
                throw new IllegalArgumentException("At least one script is required for locale: " + locale);
            }

            EnumSet<UnicodeScript> copiedScripts = EnumSet.noneOf(UnicodeScript.class);
            for (UnicodeScript script : scripts) {
                copiedScripts.add(Objects.requireNonNull(script, "scripts contains null"));
            }
            rules.put(normalizedLocale, new Rule(normalizedLocale, copiedScripts));
            return this;
        }

        public LocaleScriptPolicy build() {
            return new LocaleScriptPolicy(rules);
        }
    }
}
