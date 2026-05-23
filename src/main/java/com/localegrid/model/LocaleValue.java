package com.localegrid.model;

import org.json.JSONArray;
import org.json.JSONObject;

public class LocaleValue {
    private Object value;
    private final boolean present;
    private final boolean editable;
    private boolean modified;

    public LocaleValue(Object value, boolean present, boolean editable) {
        this.value = value;
        this.present = present;
        this.editable = editable;
    }

    public static LocaleValue missing() {
        return new LocaleValue("", false, true);
    }

    public static LocaleValue stringValue(String value, boolean present) {
        return new LocaleValue(value == null ? "" : value, present, true);
    }

    public Object getValue() {
        return value;
    }

    public String getDisplayText() {
        if (!present && !modified) {
            return "";
        }
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            return value.toString();
        }
        return String.valueOf(value);
    }

    public void setText(String text) {
        if (!editable) {
            return;
        }
        String next = text == null ? "" : text;
        if (!next.equals(getDisplayText())) {
            value = next;
            modified = true;
        }
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isEditable() {
        return editable;
    }

    public boolean isModified() {
        return modified;
    }
}
