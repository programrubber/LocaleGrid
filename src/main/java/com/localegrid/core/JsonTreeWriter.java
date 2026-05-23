package com.localegrid.core;

import com.localegrid.model.Diagnostic;

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
            if (node.children.containsKey(part)) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, "Dot path conflicts with existing object: " + key, key));
                return;
            }
            node.values.put(part, value);
            return;
        }
        if (node.values.containsKey(part)) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, "Dot path conflicts with existing leaf: " + key, key));
            return;
        }
        Node child = node.children.computeIfAbsent(part, ignored -> new Node());
        insert(child, parts, index + 1, value, diagnostics, key);
    }

    private static void appendNode(StringBuilder out, Node node, int level, int indent) {
        out.append('{');
        int count = node.children.size() + node.values.size();
        if (count == 0) {
            out.append('}');
            return;
        }
        out.append('\n');
        int index = 0;
        for (Map.Entry<String, Node> child : node.children.entrySet()) {
            appendIndent(out, level + 1, indent);
            appendQuoted(out, child.getKey());
            out.append(": ");
            appendNode(out, child.getValue(), level + 1, indent);
            if (++index < count) {
                out.append(',');
            }
            out.append('\n');
        }
        for (Map.Entry<String, Object> value : node.values.entrySet()) {
            appendIndent(out, level + 1, indent);
            appendQuoted(out, value.getKey());
            out.append(": ");
            appendValue(out, value.getValue());
            if (++index < count) {
                out.append(',');
            }
            out.append('\n');
        }
        appendIndent(out, level, indent);
        out.append('}');
    }

    private static void appendValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("\"\"");
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
            return;
        }
        if (value instanceof org.json.JSONObject || value instanceof org.json.JSONArray) {
            out.append(value);
            return;
        }
        appendQuoted(out, String.valueOf(value));
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
        private final Map<String, Node> children = new LinkedHashMap<>();
        private final Map<String, Object> values = new LinkedHashMap<>();
    }
}
