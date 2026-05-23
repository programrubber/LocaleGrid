package com.localegrid.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.localegrid.core.LocaleGridPath;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LocaleGridJsonFileEditorProvider implements FileEditorProvider, DumbAware {
    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return LocaleGridPath.isLocaleJson(project, file);
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return new LocaleGridJsonSourceEditor(TextEditorProvider.getInstance().createEditor(project, file), file);
    }

    @Override
    public @NotNull @NonNls String getEditorTypeId() {
        return "locale-grid-json-source";
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }
}
