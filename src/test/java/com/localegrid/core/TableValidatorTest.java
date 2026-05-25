package com.localegrid.core;

import com.localegrid.model.Diagnostic;
import com.localegrid.model.LocaleGridRow;
import com.localegrid.model.LocaleValue;
import com.localegrid.model.TranslationTable;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

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
}
