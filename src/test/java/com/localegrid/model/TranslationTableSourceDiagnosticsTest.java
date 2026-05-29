package com.localegrid.model;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationTableSourceDiagnosticsTest {
    @Test
    void exactDeletedKeySourceDiagnosticIsInactive() {
        TranslationTable table = tableWithSourceError("login.title");
        LocaleGridRow row = row("login.title", true);
        table.getRows().add(row);

        assertTrue(table.getActiveSourceDiagnostics().isEmpty());
    }

    @Test
    void parentSourceDiagnosticIsInactiveWhenAllNestedRowsAreDeleted() {
        TranslationTable table = tableWithSourceError("menu");
        table.getRows().add(row("menu.title", true));
        table.getRows().add(row("menu.subtitle", true));

        assertTrue(table.getActiveSourceDiagnostics().isEmpty());
    }

    @Test
    void parentSourceDiagnosticRemainsWhenNestedRowIsActive() {
        TranslationTable table = tableWithSourceError("menu");
        table.getRows().add(row("menu.title", true));
        table.getRows().add(row("menu.subtitle", false));

        assertEquals(1, table.getActiveSourceDiagnostics().size());
    }

    @Test
    void childSourceDiagnosticIsInactiveWhenOnlySiblingRowIsActive() {
        TranslationTable table = tableWithSourceError("menu.title");
        table.getRows().add(row("menu.title", true));
        table.getRows().add(row("menu.subtitle", false));

        assertTrue(table.getActiveSourceDiagnostics().isEmpty());
    }

    @Test
    void nullKeySourceDiagnosticAlwaysRemainsActive() {
        TranslationTable table = new TranslationTable("login", "ko", new File("."), List.of("ko"));
        table.getSourceDiagnostics().add(new Diagnostic(Diagnostic.Severity.ERROR, "parse failed", null));

        assertEquals(1, table.getActiveSourceDiagnostics().size());
    }

    private static TranslationTable tableWithSourceError(String key) {
        TranslationTable table = new TranslationTable("login", "ko", new File("."), List.of("ko"));
        table.getSourceDiagnostics().add(new Diagnostic(Diagnostic.Severity.ERROR, "source error", key));
        return table;
    }

    private static LocaleGridRow row(String key, boolean deleted) {
        LocaleGridRow row = new LocaleGridRow(key, LocaleGridRow.RowType.TRANSLATION);
        row.setDeleted(deleted);
        return row;
    }
}
