package com.localegrid.model;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TranslationTable {
    private final String category;
    private final String openedLocale;
    private final File localesRoot;
    private final List<String> locales;
    private final List<LocaleGridRow> rows;
    private final Map<String, File> filesByLocale;
    private final List<Diagnostic> diagnostics;

    public TranslationTable(String category, String openedLocale, File localesRoot, List<String> locales) {
        this.category = category;
        this.openedLocale = openedLocale;
        this.localesRoot = localesRoot;
        this.locales = new ArrayList<>(locales);
        this.rows = new ArrayList<>();
        this.filesByLocale = new LinkedHashMap<>();
        this.diagnostics = new ArrayList<>();
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

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.getSeverity() == Diagnostic.Severity.ERROR);
    }
}
