package com.localegrid.core;

import com.localegrid.model.Diagnostic;

import java.util.ArrayList;
import java.util.List;

public class SavePreview {
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final List<SavePreviewFile> files = new ArrayList<>();
    private int addedKeys;
    private int modifiedKeys;
    private int deletedKeys;
    private boolean orderChanged;

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    public List<SavePreviewFile> getFiles() {
        return files;
    }

    public int getAddedKeys() {
        return addedKeys;
    }

    public void incrementAddedKeys() {
        addedKeys++;
    }

    public int getModifiedKeys() {
        return modifiedKeys;
    }

    public void incrementModifiedKeys() {
        modifiedKeys++;
    }

    public int getDeletedKeys() {
        return deletedKeys;
    }

    public void incrementDeletedKeys() {
        deletedKeys++;
    }

    public boolean isOrderChanged() {
        return orderChanged;
    }

    public void setOrderChanged(boolean orderChanged) {
        this.orderChanged = orderChanged;
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.getSeverity() == Diagnostic.Severity.ERROR);
    }
}
