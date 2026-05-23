package com.localegrid.core;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.localegrid.model.Diagnostic;
import com.localegrid.model.LocaleGridRow;
import com.localegrid.model.LocaleValue;
import com.localegrid.model.TranslationTable;
import com.localegrid.settings.LocaleGridSettingsState;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TranslationTableLoader {
    public TranslationTable load(Project project, VirtualFile openedFile) {
        LocaleGridPath.LocaleFile localeFile = LocaleGridPath.resolve(project, openedFile);
        if (localeFile == null) {
            throw new IllegalArgumentException("Not a LocaleGrid JSON file.");
        }

        LocaleGridSettingsState settings = LocaleGridSettingsState.getInstance(project);
        List<String> locales = resolveLocales(localeFile.getLocalesRoot(), settings, localeFile.getLocale());
        TranslationTable table = new TranslationTable(
            localeFile.getCategory(),
            localeFile.getLocale(),
            localeFile.getLocalesRoot(),
            locales
        );

        Map<String, Map<String, LocaleValue>> valuesByLocale = new LinkedHashMap<>();
        LinkedHashSet<String> keyOrder = new LinkedHashSet<>();
        for (String locale : locales) {
            File file = new File(new File(localeFile.getLocalesRoot(), locale), localeFile.getCategory() + ".json");
            table.getFilesByLocale().put(locale, file);
            if (!file.exists()) {
                table.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.WARNING, locale + " file is missing.", null));
                valuesByLocale.put(locale, new LinkedHashMap<>());
                continue;
            }

            Map<String, LocaleValue> flattened = FlattenedJson.flatten(readFile(file, table.getDiagnostics(), locale), table.getDiagnostics(), locale);
            valuesByLocale.put(locale, flattened);
            if (locale.equals(localeFile.getLocale())) {
                keyOrder.addAll(flattened.keySet());
            }
        }

        for (Map<String, LocaleValue> values : valuesByLocale.values()) {
            keyOrder.addAll(values.keySet());
        }

        List<String> commentKeys = settings.getCommentKeyList();
        for (String key : keyOrder) {
            LocaleGridRow row = new LocaleGridRow(key, commentKeys.contains(key));
            for (String locale : locales) {
                LocaleValue value = valuesByLocale.getOrDefault(locale, Map.of()).get(key);
                row.putValue(locale, value == null ? LocaleValue.missing() : value);
            }
            table.getRows().add(row);
        }

        TableValidator.validate(table);
        return table;
    }

    private static String readFile(File file, List<Diagnostic> diagnostics, String locale) {
        try {
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
            if (virtualFile != null) {
                Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
                if (document != null) {
                    return document.getText();
                }
            }
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, locale + " file read failed: " + ex.getMessage(), null));
            return "{}";
        }
    }

    private static List<String> resolveLocales(File localesRoot, LocaleGridSettingsState settings, String openedLocale) {
        List<String> manual = settings.getManualLocaleList();
        if (!manual.isEmpty()) {
            ArrayList<String> result = new ArrayList<>(manual);
            if (!result.contains(openedLocale)) {
                result.add(0, openedLocale);
            }
            return result;
        }

        Set<String> discovered = new LinkedHashSet<>();
        File[] children = localesRoot.listFiles(File::isDirectory);
        if (children != null) {
            for (File child : children) {
                discovered.add(child.getName());
            }
        }
        discovered.add(openedLocale);
        return sortLocales(discovered);
    }

    private static List<String> sortLocales(Set<String> discovered) {
        List<String> result = new ArrayList<>();
        for (String locale : Arrays.asList("ko", "en", "jp", "vi")) {
            if (discovered.remove(locale)) {
                result.add(locale);
            }
        }
        result.addAll(discovered.stream().sorted().toList());
        return result;
    }
}
