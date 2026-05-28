package com.localegrid.core;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.localegrid.model.Diagnostic;
import com.localegrid.model.LocaleGridRow;
import com.localegrid.model.LocaleValue;
import com.localegrid.model.ExceptionKeyMarker;
import com.localegrid.model.TranslationTable;
import com.localegrid.settings.LocaleGridSettingsState;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TranslationTableSaver {
    public SavePreview preview(Project project, TranslationTable table, boolean createMissingFiles) {
        SavePreview preview = new SavePreview();
        preview.setOrderChanged(table.isOrderChanged());
        prepareValidation(table, preview.getDiagnostics());
        if (preview.hasErrors()) {
            return preview;
        }

        LocaleGridSettingsState settings = LocaleGridSettingsState.getInstance(project);
        for (String locale : table.getLocales()) {
            File file = table.getFilesByLocale().get(locale);
            if (file == null) {
                continue;
            }
            if (!file.exists() && !createMissingFiles && hasWritableValues(table, locale)) {
                preview.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.WARNING, locale + " file was not created.", null));
                continue;
            }
            if (!file.exists() && !hasWritableValues(table, locale)) {
                continue;
            }

            List<JsonRootEntry> rootEntries = buildRootEntries(table, locale, preview.getDiagnostics());
            String json = JsonTreeWriter.writeRootEntries(rootEntries, settings.jsonIndent, preview.getDiagnostics());
            if (preview.hasErrors()) {
                continue;
            }
            preview.getFiles().add(new SavePreviewFile(file.getPath(), readCurrentText(project, file), json, !file.exists()));
        }
        countChangedRows(table, preview);
        return preview;
    }

    public SaveResult save(Project project, TranslationTable table, boolean createMissingFiles) {
        SaveResult result = new SaveResult();
        result.setOrderChanged(table.isOrderChanged());
        prepareValidation(table, result.getDiagnostics());
        if (table.hasErrors()) {
            return result;
        }

        LocaleGridSettingsState settings = LocaleGridSettingsState.getInstance(project);
        WriteCommandAction.runWriteCommandAction(project, "LocaleGrid 저장", null, () -> {
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

                List<JsonRootEntry> rootEntries = buildRootEntries(table, locale, result.getDiagnostics());
                String json = JsonTreeWriter.writeRootEntries(rootEntries, settings.jsonIndent, result.getDiagnostics());
                if (result.hasErrors()) {
                    continue;
                }
                writeFile(project, file, json, result);
            }
            LocalFileSystem.getInstance().refreshIoFiles(table.getFilesByLocale().values(), false, false, null);
        });

        if (!result.hasErrors()) {
            countChangedRows(table, result);
        }

        return result;
    }

    private static void prepareValidation(TranslationTable table, List<Diagnostic> diagnostics) {
        table.getDiagnostics().clear();
        table.getDiagnostics().addAll(table.getActiveSourceDiagnostics());
        TableValidator.validate(table);
        diagnostics.addAll(table.getDiagnostics());
    }

    private static void countChangedRows(TranslationTable table, SavePreview preview) {
        int added = 0;
        int modified = 0;
        int deleted = 0;
        for (LocaleGridRow row : table.getRows()) {
            if (row.isAdded() && row.isDeleted()) {
                continue;
            }
            if (row.isDeleted()) {
                boolean wasPresent = false;
                for (String locale : table.getLocales()) {
                    if (row.getValue(locale).isPresent()) {
                        wasPresent = true;
                        break;
                    }
                }
                if (wasPresent) {
                    deleted++;
                }
            } else if (row.isAdded()) {
                added++;
            } else if (row.isModified()) {
                modified++;
            }
        }
        for (int i = 0; i < added; i++) preview.incrementAddedKeys();
        for (int i = 0; i < modified; i++) preview.incrementModifiedKeys();
        for (int i = 0; i < deleted; i++) preview.incrementDeletedKeys();
    }

    private static void countChangedRows(TranslationTable table, SaveResult result) {
        int added = 0;
        int modified = 0;
        int deleted = 0;
        for (LocaleGridRow row : table.getRows()) {
            if (row.isAdded() && row.isDeleted()) {
                continue;
            }
            if (row.isDeleted()) {
                boolean wasPresent = false;
                for (String locale : table.getLocales()) {
                    if (row.getValue(locale).isPresent()) {
                        wasPresent = true;
                        break;
                    }
                }
                if (wasPresent) {
                    deleted++;
                }
            } else if (row.isAdded()) {
                added++;
            } else if (row.isModified()) {
                modified++;
            }
        }
        for (int i = 0; i < added; i++) result.incrementAddedKeys();
        for (int i = 0; i < modified; i++) result.incrementModifiedKeys();
        for (int i = 0; i < deleted; i++) result.incrementDeletedKeys();
    }

    private static List<JsonRootEntry> buildRootEntries(TranslationTable table, String locale, List<Diagnostic> diagnostics) {
        List<JsonRootEntry> visibleEntries = new ArrayList<>();
        Set<String> emittedTranslationRoots = new HashSet<>();
        for (LocaleGridRow row : table.getRows()) {
            if (row.isDeleted()) {
                continue;
            }
            if (row.isExceptionKey()) {
                LocaleValue value = row.getValue(locale);
                if (isWritableValue(value)) {
                    visibleEntries.add(new JsonRootEntry(row.getKey(), value.getValue()));
                }
                continue;
            }

            String rootKey = topLevelGroup(row.getKey());
            if (!emittedTranslationRoots.add(rootKey)) {
                continue;
            }
            visibleEntries.addAll(buildTranslationRootEntries(table, locale, rootKey, diagnostics));
        }
        return injectHiddenExceptionKeyMarkers(visibleEntries, table.getExceptionKeyMarkers(locale));
    }

    private static List<JsonRootEntry> buildTranslationRootEntries(
        TranslationTable table,
        String locale,
        String rootKey,
        List<Diagnostic> diagnostics
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (LocaleGridRow row : table.getRows()) {
            if (row.isDeleted() || row.isExceptionKey() || !rootKey.equals(topLevelGroup(row.getKey()))) {
                continue;
            }
            LocaleValue value = row.getValue(locale);
            if (!value.isPresent() && value.getDisplayText().isEmpty()) {
                continue;
            }
            values.put(row.getKey(), value.getValue());
        }
        return JsonTreeWriter.toRootEntries(values, diagnostics);
    }

    private static List<JsonRootEntry> injectHiddenExceptionKeyMarkers(List<JsonRootEntry> visibleEntries, List<ExceptionKeyMarker> markers) {
        if (markers.isEmpty()) {
            return visibleEntries;
        }

        Set<String> visibleRootKeys = new HashSet<>();
        for (JsonRootEntry entry : visibleEntries) {
            visibleRootKeys.add(entry.getKey());
        }

        Map<String, List<ExceptionKeyMarker>> before = new LinkedHashMap<>();
        Map<String, List<ExceptionKeyMarker>> after = new LinkedHashMap<>();
        for (ExceptionKeyMarker marker : markers) {
            if (!visibleRootKeys.contains(marker.getAnchorRootKey())) {
                continue;
            }
            Map<String, List<ExceptionKeyMarker>> target = marker.getPosition() == ExceptionKeyMarker.Position.BEFORE ? before : after;
            target.computeIfAbsent(marker.getAnchorRootKey(), ignored -> new ArrayList<>()).add(marker);
        }

        List<JsonRootEntry> result = new ArrayList<>();
        for (JsonRootEntry entry : visibleEntries) {
            appendMarkers(result, before.get(entry.getKey()));
            result.add(entry);
            appendMarkers(result, after.get(entry.getKey()));
        }
        return result;
    }

    private static void appendMarkers(List<JsonRootEntry> entries, List<ExceptionKeyMarker> markers) {
        if (markers == null) {
            return;
        }
        for (ExceptionKeyMarker marker : markers) {
            entries.add(new JsonRootEntry(marker.getKey(), marker.getValue()));
        }
    }

    private static boolean hasWritableValues(TranslationTable table, String locale) {
        for (LocaleGridRow row : table.getRows()) {
            if (!row.isDeleted() && isWritableValue(row.getValue(locale))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWritableValue(LocaleValue value) {
        return value.isPresent() || !value.getDisplayText().isEmpty();
    }

    private static String topLevelGroup(String key) {
        int dot = key.indexOf('.');
        return dot < 0 ? key : key.substring(0, dot);
    }

    private static String readCurrentText(Project project, File file) {
        if (!file.exists()) {
            return "";
        }
        try {
            VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
            if (virtualFile != null) {
                Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
                if (document != null) {
                    return document.getText();
                }
            }
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private static void writeFile(Project project, File file, String json, SaveResult result) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                Files.createDirectories(parent.toPath());
            }
            if (!file.exists()) {
                Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
            }

            VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
            if (virtualFile != null) {
                FileDocumentManager documentManager = FileDocumentManager.getInstance();
                Document document = documentManager.getDocument(virtualFile);
                if (document != null) {
                    document.setText(json);
                    documentManager.saveDocument(document);
                } else {
                    virtualFile.setBinaryContent(json.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
            }
            result.getWrittenFiles().add(file.getPath());
        } catch (IOException ex) {
            result.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.ERROR, "Write failed: " + ex.getMessage(), null));
        }
    }
}
