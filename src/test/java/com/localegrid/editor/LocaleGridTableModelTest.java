package com.localegrid.editor;

import com.localegrid.model.Diagnostic;
import com.localegrid.model.LocaleGridRow;
import com.localegrid.model.LocaleValue;
import com.localegrid.model.TranslationTable;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocaleGridTableModelTest {
    @Test
    void modifiedStatusTakesPrecedenceOverWarning() {
        TranslationTable table = new TranslationTable("login", "ko", new File("."), List.of("ko"));
        LocaleGridRow row = new LocaleGridRow("login.title", false);
        LocaleValue value = LocaleValue.stringValue("로그인", true);
        row.putValue("ko", value);
        table.getRows().add(row);
        table.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.WARNING, "빈 값이 있습니다.", "login.title"));

        LocaleGridTableModel model = new LocaleGridTableModel();
        model.setTable(table);
        value.setText("로그인 수정");

        assertEquals("편집", model.getStatusCode(row));
    }

    @Test
    void applyFilterStoresNormalizedSearchTerm() {
        LocaleGridTableModel model = new LocaleGridTableModel();
        model.setTable(createTable());

        model.applyFilter("  LOGIN  ", false, false, false, false, false);

        assertEquals("login", model.getSearchTerm());
    }

    @Test
    void blankSearchTermIsCleared() {
        LocaleGridTableModel model = new LocaleGridTableModel();
        model.setTable(createTable());

        model.applyFilter("login", false, false, false, false, false);
        model.applyFilter("   ", false, false, false, false, false);

        assertEquals("", model.getSearchTerm());
        assertEquals(3, model.getRowCount());
    }

    @Test
    void filterMatchesKeyValueAndIgnoresCase() {
        LocaleGridTableModel model = new LocaleGridTableModel();
        model.setTable(createTable());

        model.applyFilter("LOGIN", false, false, false, false, false);
        assertEquals(1, model.getRowCount());
        assertEquals("login.title", model.getRow(0).getKey());

        model.applyFilter("DASHBOARD", false, false, false, false, false);
        assertEquals(1, model.getRowCount());
        assertEquals("home.title", model.getRow(0).getKey());

        model.applyFilter("설정", false, false, false, false, false);
        assertEquals(1, model.getRowCount());
        assertEquals("settings.title", model.getRow(0).getKey());
    }

    @Test
    void highlightRangesFindAllCaseInsensitiveMatches() {
        List<LocaleGridCellRenderer.HighlightRange> ranges =
            LocaleGridCellRenderer.findHighlightRanges("Login login LOGIN", "login");

        assertEquals(List.of(
            new LocaleGridCellRenderer.HighlightRange(0, 5),
            new LocaleGridCellRenderer.HighlightRange(6, 11),
            new LocaleGridCellRenderer.HighlightRange(12, 17)
        ), ranges);
    }

    @Test
    void highlightRangesKeepOriginalIndexes() {
        List<LocaleGridCellRenderer.HighlightRange> ranges =
            LocaleGridCellRenderer.findHighlightRanges("prefix Dashboard suffix", "dashboard");

        assertEquals(List.of(new LocaleGridCellRenderer.HighlightRange(7, 16)), ranges);
    }

    @Test
    void highlightRangesAreEmptyWithoutSearchOrMatch() {
        assertEquals(List.of(), LocaleGridCellRenderer.findHighlightRanges("login.title", ""));
        assertEquals(List.of(), LocaleGridCellRenderer.findHighlightRanges("login.title", "   "));
        assertEquals(List.of(), LocaleGridCellRenderer.findHighlightRanges("login.title", "missing"));
    }

    private TranslationTable createTable() {
        TranslationTable table = new TranslationTable("common", "ko", new File("."), List.of("ko", "en"));
        table.getRows().add(createRow("login.title", "로그인", "Sign in"));
        table.getRows().add(createRow("home.title", "홈", "Dashboard"));
        table.getRows().add(createRow("settings.title", "설정", "Settings"));
        return table;
    }

    private LocaleGridRow createRow(String key, String ko, String en) {
        LocaleGridRow row = new LocaleGridRow(key, false);
        row.putValue("ko", LocaleValue.stringValue(ko, true));
        row.putValue("en", LocaleValue.stringValue(en, true));
        return row;
    }
}
