package com.localegrid.core;

import java.util.Collection;

public final class DotPath {
    private DotPath() {
    }

    public static String validate(String key) {
        if (key == null || key.isEmpty()) {
            return "Key is empty.";
        }
        if (key.startsWith(".") || key.endsWith(".") || key.contains("..")) {
            return "Key has an invalid dot path.";
        }
        for (String segment : key.split("\\.")) {
            if (segment.trim().isEmpty()) {
                return "Key contains an empty segment.";
            }
        }
        return null;
    }

    public static boolean conflictsWithAny(String key, Collection<String> existingKeys) {
        for (String existing : existingKeys) {
            if (key.equals(existing)) {
                continue;
            }
            if (key.startsWith(existing + ".") || existing.startsWith(key + ".")) {
                return true;
            }
        }
        return false;
    }
}
