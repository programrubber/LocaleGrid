package com.localegrid.core;

import com.localegrid.model.Diagnostic;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonTreeWriter {
    private JsonTreeWriter() {
    }

    public static String write(Map<String, Object> flatValues, int indent, List<Diagnostic> diagnostics) {
        Node root = new Node();
        for (Map.Entry<String, Object> entry : flatValues.entrySet()) {
            String error = DotPath.validate(entry.getKey());
            if (error != null) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, error, entry.getKey()));
                continue;
            }
            insert(root, entry.getKey().split("\\."), 0, entry.getValue(), diagnostics, entry.getKey());
        }
        StringBuilder out = new StringBuilder();
        appendNode(out, root, 0, indent);
        out.append('\n');
        return out.toString();
    }

    private static void insert(Node node, String[] parts, int index, Object value, List<Diagnostic> diagnostics, String key) {
        String part = parts[index];
        if (index == parts.length - 1) {
            Entry existing = node.entries.get(part);
            if (existing != null && existing.child != null) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, "Dot path conflicts with existing object: " + key, key));
                return;
            }
            if (existing != null) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, "Duplicated dot path: " + key, key));
                return;
            }
            node.entries.put(part, Entry.value(value));
            return;
        }
        Entry existing = node.entries.get(part);
        if (existing != null && existing.child == null) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, "Dot path conflicts with existing leaf: " + key, key));
            return;
        }
        if (existing == null) {
            existing = Entry.child(new Node());
            node.entries.put(part, existing);
        }
        insert(existing.child, parts, index + 1, value, diagnostics, key);
    }

    private static void appendNode(StringBuilder out, Node node, int level, int indent) {
        out.append('{');
        int count = node.entries.size();
        if (count == 0) {
            out.append('}');
            return;
        }
        out.append('\n');
        int index = 0;
        for (Map.Entry<String, Entry> entry : node.entries.entrySet()) {
            appendIndent(out, level + 1, indent);
            appendQuoted(out, entry.getKey());
            out.append(": ");
            if (entry.getValue().child != null) {
                appendNode(out, entry.getValue().child, level + 1, indent);
            } else {
                appendValue(out, entry.getValue().value, level + 1, indent);
            }
            if (++index < count) {
                out.append(',');
            }
            out.append('\n');
        }
        appendIndent(out, level, indent);
        out.append('}');
    }

    private static void appendValue(StringBuilder out, Object value, int level, int indent) {
        if (value == null) {
            out.append("\"\"");
            return;
        }
        if (value == JSONObject.NULL) {
            out.append("null");
            return;
        }
        if (value instanceof Map) {
            appendMapValue(out, asObject(value), level, indent);
            return;
        }
        if (value instanceof List) {
            appendListValue(out, asList(value), level, indent);
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
            return;
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            out.append(value);
            return;
        }
        appendQuoted(out, String.valueOf(value));
    }

    private static void appendMapValue(StringBuilder out, Map<String, Object> value, int level, int indent) {
        out.append('{');
        if (value.isEmpty()) {
            out.append('}');
            return;
        }
        out.append('\n');
        int index = 0;
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            appendIndent(out, level + 1, indent);
            appendQuoted(out, entry.getKey());
            out.append(": ");
            appendValue(out, entry.getValue(), level + 1, indent);
            if (++index < value.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        appendIndent(out, level, indent);
        out.append('}');
    }

    private static void appendListValue(StringBuilder out, List<Object> value, int level, int indent) {
        out.append('[');
        if (value.isEmpty()) {
            out.append(']');
            return;
        }
        out.append('\n');
        for (int i = 0; i < value.size(); i++) {
            appendIndent(out, level + 1, indent);
            appendValue(out, value.get(i), level + 1, indent);
            if (i + 1 < value.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        appendIndent(out, level, indent);
        out.append(']');
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return (List<Object>) value;
    }

    private static void appendQuoted(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '"':
                    out.append("\\\"");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    out.append(c);
            }
        }
        out.append('"');
    }

    private static void appendIndent(StringBuilder out, int level, int indent) {
        out.append(" ".repeat(Math.max(0, level * indent)));
    }

    private static final class Node {
        private final Map<String, Entry> entries = new LinkedHashMap<>();
    }

    private static final class Entry {
        private final Node child;
        private final Object value;

        private Entry(Node child, Object value) {
            this.child = child;
            this.value = value;
        }

        private static Entry child(Node child) {
            return new Entry(child, null);
        }

        private static Entry value(Object value) {
            return new Entry(null, value);
        }
    }
}
