package com.localegrid.core;

public class JsonRootEntry {
    private final String key;
    private final Object value;

    public JsonRootEntry(String key, Object value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }
}
