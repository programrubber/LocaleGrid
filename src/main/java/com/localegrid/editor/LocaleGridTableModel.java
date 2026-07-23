package com.localegrid.editor;

import com.localegrid.model.Diagnostic;
import com.localegrid.model.LocaleGridRow;
import com.localegrid.model.LocaleTextEscaper;
import com.localegrid.model.LocaleValue;
import com.localegrid.model.TranslationTable;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class LocaleGridTableModel extends AbstractTableModel {
    static final String BUNDLE_COLUMN_NAME = "일괄보기";

    static final int HANDLE_COLUMN = 0;
    static final int STATUS_COLUMN = 1;
    static final int KEY_COLUMN = 2;
    private static final int LOCALE_COLUMN_OFFSET = 3;

    private TranslationTable table;
    private final List<LocaleGridRow> visibleRows = new ArrayList<>();
    private final List<String> visibleLocales = new ArrayList<>();
    private boolean bundleVisible;
    private String searchTerm = "";

    void setTable(TranslationTable table) {
        this.table = table;
        visibleLocales.clear();
        if (table != null) {
            visibleLocales.addAll(table.getLocales());
        }
        bundleVisible = false;
        searchTerm = "";
        rebuildVisibleRows("", false, false, false, false, false);
        fireTableStructureChanged();
    }

    void setVisibleLocales(Collection<String> locales) {
        visibleLocales.clear();
        if (table == null) {
            fireTableStructureChanged();
            return;
        }
        Set<String> next = new LinkedHashSet<>(locales);
        for (String locale : table.getLocales()) {
            if (next.contains(locale)) {
                visibleLocales.add(locale);
            }
        }
        fireTableStructureChanged();
    }

    void setBundleVisible(boolean bundleVisible) {
        this.bundleVisible = bundleVisible;
        fireTableStructureChanged();
    }

    boolean isBundleVisible() {
        return bundleVisible;
    }

    List<String> getVisibleLocales() {
        return new ArrayList<>(visibleLocales);
    }

    String getSearchTerm() {
        return searchTerm;
    }

    void applyFilter(String search, boolean addedOnly, boolean warningOnly, boolean modifiedOnly, boolean deletedOnly, boolean errorOnly) {
        searchTerm = normalizeSearchTerm(search);
        rebuildVisibleRows(searchTerm, addedOnly, warningOnly, modifiedOnly, deletedOnly, errorOnly);
        fireTableDataChanged();
    }

    private void rebuildVisibleRows(String search, boolean addedOnly, boolean warningOnly, boolean modifiedOnly, boolean deletedOnly, boolean errorOnly) {
        visibleRows.clear();
        if (table == null) {
            return;
        }

        String term = normalizeSearchTerm(search);
        for (LocaleGridRow row : table.getRows()) {
            if (!matchesSearch(row, term)) {
                continue;
            }
            if (addedOnly && !row.isAdded()) {
                continue;
            }
            if (warningOnly && !hasWarning(row)) {
                continue;
            }
            if (modifiedOnly && (row.isAdded() || row.isDeleted() || !row.isModified())) {
                continue;
            }
            if (deletedOnly && !row.isDeleted()) {
                continue;
            }
            if (errorOnly && !hasError(row)) {
                continue;
            }
            visibleRows.add(row);
        }
    }

    TranslationTable getTranslationTable() {
        return table;
    }

    LocaleGridRow getRow(int modelRow) {
        return visibleRows.get(modelRow);
    }

    int indexOf(LocaleGridRow row) {
        return visibleRows.indexOf(row);
    }

    void refreshRow(LocaleGridRow row) {
        int index = indexOf(row);
        if (index >= 0) {
            fireTableRowsUpdated(index, index);
        } else {
            fireTableDataChanged();
        }
    }

    boolean isKeyColumn(int column) {
        return column == KEY_COLUMN;
    }

    boolean isStatusColumn(int column) {
        return column == STATUS_COLUMN;
    }

    boolean isHandleColumn(int column) {
        return column == HANDLE_COLUMN;
    }

    boolean isBundleColumn(int column) {
        return bundleVisible && column == getColumnCount() - 1;
    }

    String getLocaleForColumn(int column) {
        if (column < LOCALE_COLUMN_OFFSET || column > visibleLocales.size() + LOCALE_COLUMN_OFFSET - 1) {
            return null;
        }
        return visibleLocales.get(column - LOCALE_COLUMN_OFFSET);
    }

    String getBundleText(LocaleGridRow row) {
        if (table == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (String locale : table.getLocales()) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(locale).append(": ").append(LocaleTextEscaper.escapeForEditor(row.getValue(locale).getDisplayText()));
        }
        return text.toString();
    }

    String getStatusCode(LocaleGridRow row) {
        List<String> codes = getStatusCodes(row);
        return codes.isEmpty() ? "" : codes.get(0);
    }

    List<String> getStatusCodes(LocaleGridRow row) {
        List<String> codes = new ArrayList<>();
        if (row.isExceptionKey()) {
            return codes;
        }
        if (row.isDeleted()) {
            codes.add("삭제");
            return codes;
        }
        if (hasError(row)) {
            codes.add("에러");
        }
        if (row.isAdded()) {
            codes.add("추가");
        } else if (row.isModified()) {
            codes.add("편집");
        }
        if (hasWarning(row)) {
            codes.add("경고");
        }
        return codes;
    }

    @Override
    public int getRowCount() {
        return visibleRows.size();
    }

    @Override
    public int getColumnCount() {
        if (table == null) {
            return 1;
        }
        return LOCALE_COLUMN_OFFSET + visibleLocales.size() + (bundleVisible ? 1 : 0);
    }

    @Override
    public String getColumnName(int column) {
        if (isHandleColumn(column)) {
            return "";
        }
        if (isStatusColumn(column)) {
            return "상태";
        }
        if (isKeyColumn(column)) {
            return "key";
        }
        if (isBundleColumn(column)) {
            return BUNDLE_COLUMN_NAME;
        }
        return visibleLocales.get(column - LOCALE_COLUMN_OFFSET);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        LocaleGridRow row = visibleRows.get(rowIndex);
        if (isHandleColumn(columnIndex)) {
            return "";
        }
        if (isStatusColumn(columnIndex)) {
            return getStatusCodes(row);
        }
        if (isKeyColumn(columnIndex)) {
            return row.getKey();
        }
        if (isBundleColumn(columnIndex)) {
            return getBundleText(row);
        }
        String locale = getLocaleForColumn(columnIndex);
        return locale == null ? "" : LocaleTextEscaper.escapeForEditor(row.getValue(locale).getDisplayText());
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    boolean hasMissing(LocaleGridRow row) {
        if (row.isExceptionKey()) {
            return false;
        }
        return row.getValues().values().stream().anyMatch(value -> value.getDisplayText().isEmpty());
    }

    boolean hasIssue(LocaleGridRow row) {
        return hasError(row) || hasWarning(row);
    }

    boolean hasError(LocaleGridRow row) {
        return hasDiagnostic(row, Diagnostic.Severity.ERROR);
    }

    boolean hasWarning(LocaleGridRow row) {
        return hasDiagnostic(row, Diagnostic.Severity.WARNING);
    }

    boolean hasError(LocaleGridRow row, int column) {
        return hasDiagnostic(row, column, Diagnostic.Severity.ERROR);
    }

    boolean hasWarning(LocaleGridRow row, int column) {
        return hasDiagnostic(row, column, Diagnostic.Severity.WARNING);
    }

    List<Diagnostic> getCellDiagnostics(LocaleGridRow row, int column) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (table == null) {
            return diagnostics;
        }
        for (Diagnostic diagnostic : table.getDiagnostics()) {
            if (appliesToCell(diagnostic, row, column)) {
                diagnostics.add(diagnostic);
            }
        }
        return diagnostics;
    }

    String getCellDiagnosticTooltip(LocaleGridRow row, int column) {
        return diagnosticTooltip(getCellDiagnostics(row, column));
    }

    List<Diagnostic> getLocaleDiagnostics(LocaleGridRow row, String locale) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (table == null) {
            return diagnostics;
        }
        for (Diagnostic diagnostic : table.getDiagnostics()) {
            if (appliesToLocale(diagnostic, row, locale)) {
                diagnostics.add(diagnostic);
            }
        }
        return diagnostics;
    }

    String getLocaleDiagnosticTooltip(LocaleGridRow row, String locale) {
        return diagnosticTooltip(getLocaleDiagnostics(row, locale));
    }

    List<Diagnostic> getRowDiagnostics(LocaleGridRow row) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (table == null) {
            return diagnostics;
        }
        for (Diagnostic diagnostic : table.getDiagnostics()) {
            if (row.getKey().equals(diagnostic.getKey())) {
                diagnostics.add(diagnostic);
            }
        }
        return diagnostics;
    }

    String getRowDiagnosticTooltip(LocaleGridRow row) {
        return diagnosticTooltip(getRowDiagnostics(row));
    }

    private static String diagnosticTooltip(List<Diagnostic> diagnostics) {
        StringBuilder tooltip = new StringBuilder();
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.getMessage() == null || diagnostic.getMessage().isBlank()) {
                continue;
            }
            if (tooltip.length() > 0) {
                tooltip.append(" / ");
            }
            tooltip.append(diagnostic.getMessage());
        }
        return tooltip.length() == 0 ? null : tooltip.toString();
    }

    private boolean hasDiagnostic(LocaleGridRow row, Diagnostic.Severity severity) {
        if (table == null) {
            return false;
        }
        return table.getDiagnostics().stream()
            .anyMatch(d -> row.getKey().equals(d.getKey()) && d.getSeverity() == severity);
    }

    private boolean hasDiagnostic(LocaleGridRow row, int column, Diagnostic.Severity severity) {
        if (table == null) {
            return false;
        }
        return table.getDiagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.getSeverity() == severity && appliesToCell(diagnostic, row, column));
    }

    private boolean appliesToCell(Diagnostic diagnostic, LocaleGridRow row, int column) {
        return appliesToLocale(diagnostic, row, getLocaleForColumn(column));
    }

    private boolean appliesToLocale(Diagnostic diagnostic, LocaleGridRow row, String locale) {
        if (!row.getKey().equals(diagnostic.getKey())) {
            return false;
        }
        String diagnosticLocale = diagnostic.getLocale();
        if (diagnosticLocale == null || diagnosticLocale.isBlank()) {
            return true;
        }
        return diagnosticLocale.equals(locale);
    }

    boolean isReadonlyCell(LocaleGridRow row, int column) {
        if (isHandleColumn(column) || isStatusColumn(column) || isKeyColumn(column) || isBundleColumn(column)) {
            return false;
        }
        String locale = getLocaleForColumn(column);
        if (locale == null) {
            return false;
        }
        LocaleValue value = row.getValue(locale);
        return value.isPresent() && !value.isEditable();
    }

    private boolean matchesSearch(LocaleGridRow row, String term) {
        if (term.isEmpty() || row.getKey().toLowerCase(Locale.ROOT).contains(term)) {
            return true;
        }
        for (LocaleValue value : row.getValues().values()) {
            if (LocaleTextEscaper.escapeForEditor(value.getDisplayText()).toLowerCase(Locale.ROOT).contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeSearchTerm(String search) {
        return search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    }
}
