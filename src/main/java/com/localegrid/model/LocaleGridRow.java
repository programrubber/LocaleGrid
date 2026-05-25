package com.localegrid.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class LocaleGridRow {
    public enum RowType {
        TRANSLATION,
        EXCEPTION_KEY
    }

    private final String originalKey;
    private final RowType originalType;
    private String key;
    private final Map<String, LocaleValue> values = new LinkedHashMap<>();
    private RowType type;
    private boolean added;
    private boolean deleted;

    public LocaleGridRow(String key, boolean exceptionKey) {
        this(key, exceptionKey ? RowType.EXCEPTION_KEY : RowType.TRANSLATION);
    }

    public LocaleGridRow(String key, RowType type) {
        this.originalKey = key;
        this.originalType = type;
        this.key = key;
        this.type = type;
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

    public boolean isExceptionKey() {
        return type == RowType.EXCEPTION_KEY;
    }

    public void setExceptionKey(boolean exceptionKey) {
        this.type = exceptionKey ? RowType.EXCEPTION_KEY : RowType.TRANSLATION;
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
        if (added || !originalKey.equals(key) || originalType != type) {
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
