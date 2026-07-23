package com.localegrid.core;

import com.localegrid.model.Diagnostic;
import com.localegrid.model.LocaleGridRow;
import com.localegrid.model.LocaleValue;
import com.localegrid.model.TranslationTable;
import com.localegrid.settings.LocaleGridSettingsState;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableValidatorTest {
    @Test
    void editedMissingLocaleValueIsNotReportedAsMissing() {
        TranslationTable table = new TranslationTable("login", "ko", new File("."), List.of("ko", "en"));
        LocaleGridRow row = new LocaleGridRow("login.message", false);
        LocaleValue en = LocaleValue.missing();
        en.setText("Message!!");
        row.putValue("ko", LocaleValue.stringValue("메시지", true));
        row.putValue("en", en);
        table.getRows().add(row);

        TableValidator.validate(table);

        assertFalse(
            table.getDiagnostics().stream().map(Diagnostic::getMessage).anyMatch(message -> message.contains("Missing or empty value")),
            table.getDiagnostics().toString()
        );
    }

    @Test
    void exceptionKeyRowsAreExcludedFromTranslationValidation() {
        TranslationTable table = new TranslationTable("login", "ko", new File("."), List.of("ko", "en"));
        LocaleGridRow first = new LocaleGridRow("__section__", LocaleGridRow.RowType.EXCEPTION_KEY);
        LocaleGridRow second = new LocaleGridRow("__section__", LocaleGridRow.RowType.EXCEPTION_KEY);
        first.putValue("ko", LocaleValue.stringValue("", true));
        second.putValue("ko", LocaleValue.stringValue("메뉴", true));
        table.getRows().add(first);
        table.getRows().add(second);

        TableValidator.validate(table);

        assertTrue(table.getDiagnostics().isEmpty(), table.getDiagnostics().toString());
    }

    @Test
    void builtInLocaleVariantsAcceptExpectedScripts() {
        List<String> locales = List.of("ko-KR", "en_US", "ja-JP", "vi-VN");
        TranslationTable table = new TranslationTable("login", "ko-KR", new File("."), locales);
        LocaleGridRow row = new LocaleGridRow("login.title", false);
        row.putValue("ko-KR", LocaleValue.stringValue("로그인 Login", true));
        row.putValue("en_US", LocaleValue.stringValue("Sign in", true));
        row.putValue("ja-JP", LocaleValue.stringValue("ログイン画面 Login", true));
        row.putValue("vi-VN", LocaleValue.stringValue("Đăng nhập bằng tiếng Việt", true));
        table.getRows().add(row);

        TableValidator.validate(table);

        assertTrue(table.getDiagnostics().isEmpty(), table.getDiagnostics().toString());
    }

    @Test
    void unexpectedScriptCreatesLocaleScopedWarning() {
        TranslationTable table = new TranslationTable("login", "en", new File("."), List.of("en"));
        LocaleGridRow row = new LocaleGridRow("login.title", false);
        row.putValue("en", LocaleValue.stringValue("Sign in 로그인", true));
        table.getRows().add(row);

        TableValidator.validate(table);

        List<Diagnostic> localeDiagnostics = table.getDiagnostics().stream()
            .filter(diagnostic -> diagnostic.getLocale() != null)
            .toList();
        assertEquals(1, localeDiagnostics.size());
        assertEquals(Diagnostic.Severity.WARNING, localeDiagnostics.get(0).getSeverity());
        assertEquals("login.title", localeDiagnostics.get(0).getKey());
        assertEquals("en", localeDiagnostics.get(0).getLocale());
        assertTrue(localeDiagnostics.get(0).getMessage().contains("허용되지 않은 문자"));
        assertFalse(table.hasErrors());
    }

    @Test
    void localeScriptValidationCanBeDisabled() {
        TranslationTable table = new TranslationTable("login", "en", new File("."), List.of("en"));
        LocaleGridRow row = new LocaleGridRow("login.title", false);
        row.putValue("en", LocaleValue.stringValue("로그인", true));
        table.getRows().add(row);
        LocaleGridSettingsState settings = new LocaleGridSettingsState();
        settings.localeScriptValidationEnabled = false;

        TableValidator.validate(table, settings);

        assertTrue(table.getDiagnostics().isEmpty(), table.getDiagnostics().toString());
    }

    @Test
    void strictLocaleScriptValidationCreatesBlockingError() {
        TranslationTable table = new TranslationTable("login", "vi", new File("."), List.of("vi"));
        LocaleGridRow row = new LocaleGridRow("login.title", false);
        row.putValue("vi", LocaleValue.stringValue("Đăng nhập Привет", true));
        table.getRows().add(row);
        LocaleGridSettingsState settings = new LocaleGridSettingsState();
        settings.localeScriptViolationSeverity = "ERROR";

        TableValidator.validate(table, settings);

        assertTrue(table.hasErrors());
        assertTrue(table.getDiagnostics().stream().anyMatch(diagnostic ->
            "vi".equals(diagnostic.getLocale())
                && diagnostic.getSeverity() == Diagnostic.Severity.ERROR
        ));
    }
}
