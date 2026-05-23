package com.localegrid.core;

import com.localegrid.model.Diagnostic;

import java.util.ArrayList;
import java.util.List;

public class SaveResult {
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final List<String> writtenFiles = new ArrayList<>();
    private final List<String> createdFiles = new ArrayList<>();
    private int addedKeys;
    private int modifiedKeys;
    private int deletedKeys;

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    public List<String> getWrittenFiles() {
        return writtenFiles;
    }

    public List<String> getCreatedFiles() {
        return createdFiles;
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

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.getSeverity() == Diagnostic.Severity.ERROR);
    }
}
