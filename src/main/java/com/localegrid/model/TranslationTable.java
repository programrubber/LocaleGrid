package com.localegrid.model;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TranslationTable {
    private final String category;
    private final String openedLocale;
    private final File localesRoot;
    private final List<String> locales;
    private final List<LocaleGridRow> rows;
    private final Map<String, File> filesByLocale;
    private final Map<String, List<ExceptionKeyMarker>> exceptionKeyMarkersByLocale;
    private final List<Diagnostic> diagnostics;
    private final List<Diagnostic> sourceDiagnostics;
    private boolean orderChanged;

    public TranslationTable(String category, String openedLocale, File localesRoot, List<String> locales) {
        this.category = category;
        this.openedLocale = openedLocale;
        this.localesRoot = localesRoot;
        this.locales = new ArrayList<>(locales);
        this.rows = new ArrayList<>();
        this.filesByLocale = new LinkedHashMap<>();
        this.exceptionKeyMarkersByLocale = new LinkedHashMap<>();
        this.diagnostics = new ArrayList<>();
        this.sourceDiagnostics = new ArrayList<>();
    }

    public String getCategory() {
        return category;
    }

    public String getOpenedLocale() {
        return openedLocale;
    }

    public File getLocalesRoot() {
        return localesRoot;
    }

    public List<String> getLocales() {
        return locales;
    }

    public List<LocaleGridRow> getRows() {
        return rows;
    }

    public Map<String, File> getFilesByLocale() {
        return filesByLocale;
    }

    public Map<String, List<ExceptionKeyMarker>> getExceptionKeyMarkersByLocale() {
        return exceptionKeyMarkersByLocale;
    }

    public List<ExceptionKeyMarker> getExceptionKeyMarkers(String locale) {
        return exceptionKeyMarkersByLocale.getOrDefault(locale, List.of());
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    public List<Diagnostic> getSourceDiagnostics() {
        return sourceDiagnostics;
    }

    public boolean isOrderChanged() {
        return orderChanged;
    }

    public void markOrderChanged() {
        orderChanged = true;
    }

    public List<Diagnostic> getActiveSourceDiagnostics() {
        Set<String> deletedKeys = rows.stream()
            .filter(LocaleGridRow::isDeleted)
            .map(LocaleGridRow::getKey)
            .collect(Collectors.toSet());
        if (deletedKeys.isEmpty()) {
            return sourceDiagnostics;
        }
        return sourceDiagnostics.stream()
            .filter(d -> d.getKey() == null || !deletedKeys.contains(d.getKey()))
            .collect(Collectors.toList());
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.getSeverity() == Diagnostic.Severity.ERROR);
    }
}
