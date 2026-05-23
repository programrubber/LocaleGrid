package com.localegrid.core;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.localegrid.model.Diagnostic;
import com.localegrid.model.LocaleGridRow;
import com.localegrid.model.LocaleValue;
import com.localegrid.model.TranslationTable;
import com.localegrid.settings.LocaleGridSettingsState;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public class TranslationTableSaver {
    public SaveResult save(Project project, TranslationTable table, boolean createMissingFiles) {
        SaveResult result = new SaveResult();
        table.getDiagnostics().clear();
        TableValidator.validate(table);
        result.getDiagnostics().addAll(table.getDiagnostics());
        if (table.hasErrors()) {
            return result;
        }

        LocaleGridSettingsState settings = LocaleGridSettingsState.getInstance(project);
        WriteCommandAction.runWriteCommandAction(project, "Save LocaleGrid", null, () -> {
            for (String locale : table.getLocales()) {
                File file = table.getFilesByLocale().get(locale);
                if (file == null) {
                    continue;
                }
                if (!file.exists() && !createMissingFiles && hasWritableValues(table, locale)) {
                    result.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.WARNING, locale + " file was not created.", null));
                    continue;
                }
                if (!file.exists() && hasWritableValues(table, locale)) {
                    result.getCreatedFiles().add(file.getPath());
                }
                if (!file.exists() && !hasWritableValues(table, locale)) {
                    continue;
                }

                Map<String, Object> flatValues = buildFlatValues(table, locale, result);
                String json = JsonTreeWriter.write(flatValues, settings.jsonIndent, result.getDiagnostics());
                if (result.hasErrors()) {
                    continue;
                }
                writeFile(file, json, result);
            }
            ApplicationManager.getApplication().invokeLater(() ->
                LocalFileSystem.getInstance().refreshIoFiles(table.getFilesByLocale().values())
            );
        });
        return result;
    }

    private static Map<String, Object> buildFlatValues(TranslationTable table, String locale, SaveResult result) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (LocaleGridRow row : table.getRows()) {
            LocaleValue value = row.getValue(locale);
            if (row.isDeleted()) {
                if (value.isPresent()) {
                    result.incrementDeletedKeys();
                }
                continue;
            }
            if (!value.isPresent() && value.getDisplayText().isEmpty()) {
                continue;
            }
            values.put(row.getKey(), value.getValue());
            if (!value.isPresent()) {
                result.incrementAddedKeys();
            } else if (value.isModified()) {
                result.incrementModifiedKeys();
            }
        }
        return values;
    }

    private static boolean hasWritableValues(TranslationTable table, String locale) {
        for (LocaleGridRow row : table.getRows()) {
            if (!row.isDeleted() && !row.getValue(locale).getDisplayText().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void writeFile(File file, String json, SaveResult result) {
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
            result.getWrittenFiles().add(file.getPath());
        } catch (IOException ex) {
            result.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.ERROR, "Write failed: " + ex.getMessage(), null));
        }
    }
}
