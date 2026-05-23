package com.localegrid.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.util.List;

class LocaleGridJsonSourceEditor extends UserDataHolderBase implements FileEditor {
    private final FileEditor delegate;
    private final VirtualFile file;

    LocaleGridJsonSourceEditor(FileEditor delegate, VirtualFile file) {
        this.delegate = delegate;
        this.file = file;
    }

    @Override
    public @NotNull JComponent getComponent() {
        return delegate.getComponent();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return delegate.getPreferredFocusedComponent();
    }

    @Override
    public @NotNull String getName() {
        return "JSON";
    }

    @Override
    public @NotNull VirtualFile getFile() {
        return file;
    }

    @Override
    public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
        return delegate.getState(level);
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
        delegate.setState(state);
    }

    @Override
    public void setState(@NotNull FileEditorState state, boolean exactState) {
        delegate.setState(state, exactState);
    }

    @Override
    public boolean isModified() {
        return delegate.isModified();
    }

    @Override
    public boolean isValid() {
        return delegate.isValid();
    }

    @Override
    public void selectNotify() {
        delegate.selectNotify();
    }

    @Override
    public void deselectNotify() {
        delegate.deselectNotify();
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
        delegate.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
        delegate.removePropertyChangeListener(listener);
    }

    @Override
    public @Nullable BackgroundEditorHighlighter getBackgroundHighlighter() {
        return delegate.getBackgroundHighlighter();
    }

    @Override
    public @Nullable FileEditorLocation getCurrentLocation() {
        return delegate.getCurrentLocation();
    }

    @Override
    public @Nullable StructureViewBuilder getStructureViewBuilder() {
        return delegate.getStructureViewBuilder();
    }

    @Override
    public @NotNull List<VirtualFile> getFilesToRefresh() {
        return delegate.getFilesToRefresh();
    }

    @Override
    public @Nullable ActionGroup getTabActions() {
        return delegate.getTabActions();
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }
}
