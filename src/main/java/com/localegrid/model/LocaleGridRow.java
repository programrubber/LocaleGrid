package com.localegrid.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class LocaleGridRow {
    private final String originalKey;
    private String key;
    private final Map<String, LocaleValue> values = new LinkedHashMap<>();
    private final boolean comment;
    private boolean added;
    private boolean deleted;

    public LocaleGridRow(String key, boolean comment) {
        this.originalKey = key;
        this.key = key;
        this.comment = comment;
    }

    public String getKey() {
        return key;
    }

    public void rename(String nextKey) {
        this.key = nextKey;
    }

    public Map<String, LocaleValue> getValues() {
        return values;
    }

    public LocaleValue getValue(String locale) {
        return values.getOrDefault(locale, LocaleValue.missing());
    }

    public void putValue(String locale, LocaleValue value) {
        values.put(locale, value);
    }

    public boolean isComment() {
        return comment;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void markAdded() {
        this.added = true;
    }

    public boolean isAdded() {
        return added;
    }

    public boolean isModified() {
        if (added || !originalKey.equals(key)) {
            return true;
        }
        if (deleted) {
            return true;
        }
        for (LocaleValue value : values.values()) {
            if (value.isModified()) {
                return true;
            }
        }
        return false;
    }
}
