package com.localegrid.core;

public class SavePreviewFile {
    private final String path;
    private final String originalText;
    private final String newText;
    private final boolean created;

    public SavePreviewFile(String path, String originalText, String newText, boolean created) {
        this.path = path;
        this.originalText = originalText == null ? "" : originalText;
        this.newText = newText == null ? "" : newText;
        this.created = created;
    }

    public String getPath() {
        return path;
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getNewText() {
        return newText;
    }

    public boolean isCreated() {
        return created;
    }

    public boolean hasChanges() {
        return created || !originalText.equals(newText);
    }
}
