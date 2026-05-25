package com.localegrid.model;

public class ExceptionKeyMarker {
    public enum Position {
        BEFORE,
        AFTER
    }

    private final String key;
    private final Object value;
    private final String anchorRootKey;
    private final Position position;

    public ExceptionKeyMarker(String key, Object value, String anchorRootKey, Position position) {
        this.key = key;
        this.value = value;
        this.anchorRootKey = anchorRootKey;
        this.position = position;
    }

    public String getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    public String getAnchorRootKey() {
        return anchorRootKey;
    }

    public Position getPosition() {
        return position;
    }
}
