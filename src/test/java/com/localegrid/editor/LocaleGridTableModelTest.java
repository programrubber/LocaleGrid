package com.localegrid.editor;

import com.localegrid.model.Diagnostic;
import com.localegrid.model.LocaleGridRow;
import com.localegrid.model.LocaleValue;
import com.localegrid.model.TranslationTable;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JTable;
import java.awt.Color;
import java.awt.Component;
import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void localeDiagnosticAppliesOnlyToMatchingLocaleCellWhileRowStatusRemainsAggregated() {
        TranslationTable table = createTable();
        LocaleGridRow row = table.getRows().get(0);
        table.getDiagnostics().add(new Diagnostic(
            Diagnostic.Severity.WARNING,
            "영어 값에 허용되지 않은 문자가 있습니다.",
            row.getKey(),
            "en"
        ));

        LocaleGridTableModel model = new LocaleGridTableModel();
        model.setTable(table);
        int koColumn = LocaleGridTableModel.KEY_COLUMN + 1;
        int enColumn = LocaleGridTableModel.KEY_COLUMN + 2;

        assertTrue(model.hasWarning(row));
        assertEquals(List.of("경고"), model.getStatusCodes(row));
        assertFalse(model.hasWarning(row, koColumn));
        assertTrue(model.hasWarning(row, enColumn));
        assertNull(model.getCellDiagnosticTooltip(row, koColumn));
        assertEquals("영어 값에 허용되지 않은 문자가 있습니다.", model.getCellDiagnosticTooltip(row, enColumn));
    }

    @Test
    void structuralDiagnosticAppliesToEveryRelatedCell() {
        TranslationTable table = createTable();
        LocaleGridRow row = table.getRows().get(0);
        table.getDiagnostics().add(new Diagnostic(
            Diagnostic.Severity.ERROR,
            "dot path 충돌이 있습니다.",
            row.getKey()
        ));

        LocaleGridTableModel model = new LocaleGridTableModel();
        model.setTable(table);

        assertTrue(model.hasError(row, LocaleGridTableModel.KEY_COLUMN));
        assertTrue(model.hasError(row, 3));
        assertTrue(model.hasError(row, 4));
        assertEquals("dot path 충돌이 있습니다.", model.getCellDiagnosticTooltip(row, 3));
    }

    @Test
    void cellTooltipIncludesAllApplicableDiagnosticMessages() {
        TranslationTable table = createTable();
        LocaleGridRow row = table.getRows().get(0);
        table.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.WARNING, "구조 경고", row.getKey()));
        table.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.WARNING, "한국어 경고", row.getKey(), "ko"));
        table.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.WARNING, "영어 경고", row.getKey(), "en"));

        LocaleGridTableModel model = new LocaleGridTableModel();
        model.setTable(table);

        assertEquals("구조 경고 / 한국어 경고", model.getCellDiagnosticTooltip(row, 3));
        assertEquals("구조 경고 / 영어 경고", model.getCellDiagnosticTooltip(row, 4));
    }

    @Test
    void localeTooltipRemainsAvailableWhenItsGridColumnIsHidden() {
        TranslationTable table = createTable();
        LocaleGridRow row = table.getRows().get(0);
        table.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.WARNING, "영어 문자 체계 경고", row.getKey(), "en"));

        LocaleGridTableModel model = new LocaleGridTableModel();
        model.setTable(table);
        model.setVisibleLocales(List.of("ko"));

        assertEquals(List.of("ko"), model.getVisibleLocales());
        assertEquals("영어 문자 체계 경고", model.getLocaleDiagnosticTooltip(row, "en"));
        assertEquals("영어 문자 체계 경고", model.getRowDiagnosticTooltip(row));
        assertEquals(1, model.getLocaleDiagnostics(row, "en").size());
        assertNull(model.getLocaleDiagnosticTooltip(row, "ko"));
    }

    @Test
    void statusTooltipUsesGeneralValidationDescriptions() {
        assertEquals(
            "경고 - 번역 값에 확인이 필요한 검증 경고가 있습니다.",
            LocaleGridStatusRenderer.tooltipText("경고")
        );
        assertEquals(
            "에러 - 적용을 차단하는 검증 오류가 있어 확인이 필요합니다.",
            LocaleGridStatusRenderer.tooltipText("에러")
        );
    }

    @Test
    void rendererHighlightsAndDescribesOnlyTheDiagnosedLocaleCell() {
        TranslationTable table = createTable();
        LocaleGridRow row = table.getRows().get(0);
        table.getDiagnostics().add(new Diagnostic(
            Diagnostic.Severity.WARNING,
            "영어 셀 경고",
            row.getKey(),
            "en"
        ));
        LocaleGridTableModel model = new LocaleGridTableModel();
        model.setTable(table);
        JTable grid = new JTable(model);
        LocaleGridCellRenderer renderer = new LocaleGridCellRenderer();

        Component koCell = renderer.getTableCellRendererComponent(
            grid,
            model.getValueAt(0, 3),
            false,
            false,
            0,
            3
        );
        Color koBackground = koCell.getBackground();
        String koTooltip = ((JComponent) koCell).getToolTipText();
        Component enCell = renderer.getTableCellRendererComponent(
            grid,
            model.getValueAt(0, 4),
            false,
            false,
            0,
            4
        );

        assertNotEquals(koBackground, enCell.getBackground());
        assertNull(koTooltip);
        assertEquals("영어 셀 경고", ((JComponent) enCell).getToolTipText());
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
    void statusFiltersMatchAnySelectedStatus() {
        TranslationTable table = createTable();
        table.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.WARNING, "확인이 필요합니다.", "login.title"));
        table.getRows().get(1).getValue("ko").setText("홈 수정");

        LocaleGridTableModel model = new LocaleGridTableModel();
        model.setTable(table);

        model.applyFilter("", false, true, true, false, false);

        assertEquals(2, model.getRowCount());
        assertEquals("login.title", model.getRow(0).getKey());
        assertEquals("home.title", model.getRow(1).getKey());
    }

    @Test
    void searchStillNarrowsStatusFilterResults() {
        TranslationTable table = createTable();
        table.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.WARNING, "확인이 필요합니다.", "login.title"));
        table.getRows().get(1).getValue("ko").setText("홈 수정");

        LocaleGridTableModel model = new LocaleGridTableModel();
        model.setTable(table);

        model.applyFilter("login", false, true, true, false, false);

        assertEquals(1, model.getRowCount());
        assertEquals("login.title", model.getRow(0).getKey());
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
