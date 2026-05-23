package com.localegrid.core;

import java.util.Collection;

public final class DotPath {
    private DotPath() {
    }

    public static String validate(String key) {
        if (key == null || key.isEmpty()) {
            return "key가 비어 있습니다.";
        }
        if (key.startsWith(".") || key.endsWith(".") || key.contains("..")) {
            return "dot path 형식이 올바르지 않습니다.";
        }
        for (String segment : key.split("\\.")) {
            if (segment.trim().isEmpty()) {
                return "dot path에 빈 segment가 있습니다.";
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
