package com.localegrid.core;

import org.junit.jupiter.api.Test;

import java.lang.Character.UnicodeScript;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocaleScriptValidatorTest {
    private final LocaleScriptValidator validator = new LocaleScriptValidator();

    @Test
    void acceptsBuiltInLanguagesAndLocaleVariants() {
        assertValid("ko-KR", "로그인 Login 123! 😀");
        assertValid("en_US", "Sign in 123! 😀");
        assertValid("JA-jP", "ログイン設定・漢字 Login 123! 😀");
        assertValid("vi-VN", "Đăng nhập bằng tiếng Việt 123! 😀");
        assertValid("ko", "Tiếng Việt et français");
        assertValid("ja", "Tiếng Việt et français");
    }

    @Test
    void commonAndInheritedCharactersAreAlwaysAllowed() {
        assertValid("en", "Cafe\u0301 — (123) 😀");
        assertValid("en", "New emoji 🫨");
        assertValid("vi", "Vie\u0323t Nam…");
        assertValid("ko", "한글\nEnglish\t#1");
        assertValid("ja", "カタカナ・ひらがな「漢字」");
    }

    @Test
    void unicodeNumbersAndNewEmojiRangeAreUniversallyAllowedButPrivateUseIsRejected() {
        assertValid("en", "Numbers ١ २ Ⅻ");
        assertValid("en", "New digits " + codePoint(0x1E4F0) + " " + codePoint(0x11F50));
        assertValid("en", "New emoji 🫨");

        LocaleScriptValidator.ValidationResult privateUse = validator.validate("en", "Private \uE000");

        assertTrue(privateUse.isChecked());
        assertFalse(privateUse.isValid());
        assertEquals(Set.of(UnicodeScript.UNKNOWN), privateUse.getUnexpectedScripts());
    }

    @Test
    void explicitScriptSubtagsAreRespectedForBuiltInLanguages() {
        assertValid("ja-Latn", "Japanese in Latin only");
        assertUnexpectedScript("ja-Latn", "ログイン", UnicodeScript.KATAKANA);
        assertValid("ko_LaTn_KR", "Korean in Latin only");
        assertUnexpectedScript("ko-Latn", "한글", UnicodeScript.HANGUL);

        assertValid("ja-Jpan-JP", "ログイン・ひらがな・漢字 Login");
        assertValid("ko-Kore-KR", "한글 Login");
        assertValid("ja-Hrkt", "ひらがな カタカナ Latin");
        assertUnexpectedScript("ja-Hrkt", "漢字", UnicodeScript.HAN);
        assertValid("ja-Hans", "漢字 Latin");
        assertValid("ja-Hant", "漢字 Latin");
        assertValid("ja-Hani", "漢字 Latin");
    }

    @Test
    void unicodeScriptAliasesAreResolvedSafelyForBuiltInLanguages() {
        assertValid("ja-Cyrl", "Привет Latin");
        assertUnexpectedScript("ja-Cyrl", "ひらがな", UnicodeScript.HIRAGANA);

        LocaleScriptValidator.ValidationResult unknownAlias = validator.validate("ja-Qaaa", "日本語");
        LocaleScriptValidator.ValidationResult unknownScript = validator.validate("ja-Zzzz", "日本語");
        assertFalse(unknownAlias.isChecked());
        assertTrue(unknownAlias.isValid());
        assertFalse(unknownScript.isChecked());
        assertTrue(unknownScript.isValid());
    }

    @Test
    void compositeAndVariantIsoScriptSubtagsMapToRuntimeScripts() {
        assertValid("zh-Hanb", "漢字 ㄅㄆ Latin");
        assertUnexpectedScript("zh-Hanb", "한글", UnicodeScript.HANGUL);
        assertValid("de-Latf", "Fraktur Latin");
        assertValid("cu-Cyrs", "Слово Latin");
        assertValid("syr-Syre", "ܫܠܡܐ Latin");
        assertValid("ka-Geok", "ქართული Latin");
    }

    @Test
    void undeterminedLanguageCanStillUseAnExplicitScript() {
        assertValid("und-Latn", "Latin text");
        assertUnexpectedScript("und-Latn", "한글", UnicodeScript.HANGUL);

        LocaleScriptValidator.ValidationResult undetermined = validator.validate("und", "Latin text");
        assertFalse(undetermined.isChecked());
    }

    @Test
    void cldrLikelySubtagsSupportAdditionalLanguages() {
        assertValid("ru-RU", "Привет Login");
        assertUnexpectedScript("ru", "مرحبا", UnicodeScript.ARABIC);
        assertValid("ar", "مرحبا Login");
        assertUnexpectedScript("ar-EG", "नमस्ते", UnicodeScript.DEVANAGARI);
        assertValid("hi_IN", "नमस्ते Login");
        assertUnexpectedScript("hi", "Привет", UnicodeScript.CYRILLIC);
    }

    @Test
    void icuScriptFallbackBridgesNewerUnicodeDataThanTheJavaRuntime() {
        assertValid("ar", "Arabic " + codePoint(0x0870));
        assertValid("en", "Latin " + codePoint(0x10780));
        assertValid("ja", "漢字 " + codePoint(0x31350));
        assertValid("txo", codePoint(0x1E290));
        assertValid("unr-Nagm", codePoint(0x1E4D0));
        assertValid("jv-Kawi", codePoint(0x11F02));
        assertValid("sq-Vith", codePoint(0x10570));

        LocaleScriptValidator.ValidationResult foreignNewScript = validator.validate("en", codePoint(0x1E290));
        assertFalse(foreignNewScript.isValid());
        assertEquals(Set.of(UnicodeScript.UNKNOWN), foreignNewScript.getUnexpectedScripts());
        assertTrue(foreignNewScript.summarize().contains("Toto"), foreignNewScript.summarize());
    }

    @Test
    void reportsUnexpectedCharactersAndScripts() {
        LocaleScriptValidator.ValidationResult result = validator.validate("en", "Hello 한한Ж");

        assertTrue(result.isChecked());
        assertFalse(result.isValid());
        assertEquals(3, result.getViolations().size());
        assertEquals(List.of("한", "Ж"), result.getUnexpectedCharacters());
        assertEquals(Set.of(UnicodeScript.HANGUL, UnicodeScript.CYRILLIC), result.getUnexpectedScripts());
        assertTrue(result.summarize().contains("한"), result.summarize());
        assertTrue(result.summarize().contains("Ж"), result.summarize());
        assertTrue(result.summarize().contains("한글"), result.summarize());
        assertTrue(result.summarize().contains("키릴"), result.summarize());
    }

    @Test
    void reportsSupplementaryCodePointAsOneViolation() {
        String deseretLetter = new String(Character.toChars(0x10400));

        LocaleScriptValidator.ValidationResult result = validator.validate("en", "Hello " + deseretLetter);

        assertFalse(result.isValid());
        assertEquals(1, result.getViolations().size());
        assertEquals(deseretLetter, result.getViolations().get(0).getCharacter());
        assertEquals(0x10400, result.getViolations().get(0).getCodePoint());
        assertEquals(UnicodeScript.DESERET, result.getViolations().get(0).getScript());
    }

    @Test
    void rejectsForeignScriptsForEachBuiltInLanguage() {
        assertUnexpectedScript("ko", "한국어 カタカナ", UnicodeScript.KATAKANA);
        assertUnexpectedScript("en", "English 한국어", UnicodeScript.HANGUL);
        assertUnexpectedScript("ja", "日本語 한국어", UnicodeScript.HANGUL);
        assertUnexpectedScript("vi", "Tiếng Việt Привет", UnicodeScript.CYRILLIC);
    }

    @Test
    void skipsUnknownAndMalformedLocales() {
        LocaleScriptValidator.ValidationResult unknown = validator.validate("xx-XX", "한국어 日本語");
        LocaleScriptValidator.ValidationResult malformed = validator.validate("not_a_locale!", "한국어");
        LocaleScriptValidator.ValidationResult incompleteUnicodeExtension = validator.validate("en-u", "한국어");
        LocaleScriptValidator.ValidationResult incompletePrivateUse = validator.validate("en-x", "한국어");
        LocaleScriptValidator.ValidationResult repeatedSeparator = validator.validate("en--US", "한국어");
        LocaleScriptValidator.ValidationResult shortVariant = validator.validate("en-US-abc", "한국어");

        assertFalse(unknown.isChecked());
        assertTrue(unknown.isValid());
        assertTrue(unknown.getViolations().isEmpty());
        assertFalse(malformed.isChecked());
        assertTrue(malformed.isValid());
        assertFalse(incompleteUnicodeExtension.isChecked());
        assertFalse(incompletePrivateUse.isChecked());
        assertFalse(repeatedSeparator.isChecked());
        assertFalse(shortVariant.isChecked());
    }

    @Test
    void customRulesCanOverrideSpecificLocaleBeforeLanguageFallback() {
        LocaleScriptPolicy policy = LocaleScriptPolicy.defaults().toBuilder()
            .allow("sr", UnicodeScript.CYRILLIC, UnicodeScript.LATIN)
            .allow("sr-Latn", UnicodeScript.LATIN)
            .build();
        LocaleScriptValidator customValidator = new LocaleScriptValidator(policy);

        assertTrue(customValidator.validate("sr-RS", "Пријава Login").isValid());
        assertTrue(customValidator.validate("sr-Latn-RS", "Prijava").isValid());
        assertFalse(customValidator.validate("sr-Latn-RS", "Пријава").isValid());
    }

    @Test
    void explicitScriptSubtagOverridesACustomLanguageFallback() {
        LocaleScriptPolicy policy = LocaleScriptPolicy.builder()
            .allow("sr", UnicodeScript.CYRILLIC)
            .build();
        LocaleScriptValidator customValidator = new LocaleScriptValidator(policy);

        assertTrue(customValidator.validate("sr-RS", "Пријава").isValid());
        assertFalse(customValidator.validate("sr-RS", "Login").isValid());
        assertTrue(customValidator.validate("sr-Latn-RS", "Prijava").isValid());
        assertFalse(customValidator.validate("sr-Latn-RS", "Пријава").isValid());
    }

    @Test
    void reusesCompiledPatternForLocalesWithTheSameScripts() {
        validator.validate("en", "Hello");
        validator.validate("en-US", "Hello");
        validator.validate("vi-VN", "Xin chào");

        assertEquals(1, validator.cachedPatternCount());

        validator.validate("ko", "한글");
        assertEquals(2, validator.cachedPatternCount());
    }

    private void assertValid(String locale, String text) {
        LocaleScriptValidator.ValidationResult result = validator.validate(locale, text);
        assertTrue(result.isChecked(), "Expected a policy for " + locale);
        assertTrue(result.isValid(), result.summarize());
    }

    private void assertUnexpectedScript(String locale, String text, UnicodeScript script) {
        LocaleScriptValidator.ValidationResult result = validator.validate(locale, text);
        assertTrue(result.isChecked(), "Expected a policy for " + locale);
        assertFalse(result.isValid());
        assertTrue(result.getUnexpectedScripts().contains(script), result.summarize());
    }

    private static String codePoint(int codePoint) {
        return new String(Character.toChars(codePoint));
    }
}
