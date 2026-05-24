package com.localegrid.core;

import com.localegrid.model.Diagnostic;
import com.localegrid.model.LocaleValue;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlattenedJson {
    private FlattenedJson() {
    }

    public static Map<String, LocaleValue> flatten(String content, List<Diagnostic> diagnostics, String locale) {
        Map<String, LocaleValue> values = new LinkedHashMap<>();
        String json = content == null || content.trim().isEmpty() ? "{}" : content;
        try {
            Object parsed = OrderedParser.parse(json, diagnostics, locale);
            if (!(parsed instanceof Map)) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, locale + " JSON root must be an object.", null));
                return values;
            }
            flattenObject("", asObject(parsed), values, diagnostics, locale);
        } catch (JSONException ex) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, locale + " JSON parse failed: " + ex.getMessage(), null));
        }
        return values;
    }

    private static void flattenObject(
        String prefix,
        Map<String, Object> object,
        Map<String, LocaleValue> values,
        List<Diagnostic> diagnostics,
        String locale
    ) {
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            String key = prefix.isEmpty() ? name : prefix + "." + name;
            if (value instanceof Map) {
                flattenObject(key, asObject(value), values, diagnostics, locale);
            } else {
                if (values.containsKey(key)) {
                    diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, locale + " has duplicated dot path: " + key, key));
                }
                boolean editable = value instanceof String || value == JSONObject.NULL || value == null;
                if (value instanceof List || value instanceof JSONArray) {
                    editable = false;
                }
                values.put(key, new LocaleValue(value == JSONObject.NULL ? "" : value, true, editable));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) {
        return (Map<String, Object>) value;
    }

    private static final class OrderedParser {
        private OrderedParser() {
        }

        private static Object parse(String json, List<Diagnostic> diagnostics, String locale) {
            JSONTokener tokener = new JSONTokener(json);
            Object value = nextValue(tokener, diagnostics, locale, "");
            if (tokener.nextClean() != 0) {
                throw tokener.syntaxError("Unexpected trailing content");
            }
            return value;
        }

        private static Object nextValue(JSONTokener tokener, List<Diagnostic> diagnostics, String locale, String path) {
            char c = tokener.nextClean();
            switch (c) {
                case '"':
                case '\'':
                    return tokener.nextString(c);
                case '{':
                    return nextObject(tokener, diagnostics, locale, path);
                case '[':
                    return nextArray(tokener, diagnostics, locale, path);
                default:
                    tokener.back();
                    return tokener.nextValue();
            }
        }

        private static Map<String, Object> nextObject(JSONTokener tokener, List<Diagnostic> diagnostics, String locale, String path) {
            Map<String, Object> object = new LinkedHashMap<>();
            char c = tokener.nextClean();
            if (c == '}') {
                return object;
            }
            tokener.back();

            while (true) {
                String key = nextKey(tokener);
                c = tokener.nextClean();
                if (c != ':') {
                    throw tokener.syntaxError("Expected ':' after key");
                }
                String childPath = path.isEmpty() ? key : path + "." + key;
                if (object.containsKey(key)) {
                    diagnostics.add(new Diagnostic(
                        Diagnostic.Severity.ERROR,
                        locale + " has duplicated JSON key: " + childPath,
                        childPath
                    ));
                }
                object.put(key, nextValue(tokener, diagnostics, locale, childPath));

                c = tokener.nextClean();
                if (c == '}') {
                    return object;
                }
                if (c != ',') {
                    throw tokener.syntaxError("Expected ',' or '}'");
                }
            }
        }

        private static String nextKey(JSONTokener tokener) {
            char c = tokener.nextClean();
            if (c == '"' || c == '\'') {
                return tokener.nextString(c);
            }
            tokener.back();
            Object key = tokener.nextValue();
            return String.valueOf(key);
        }

        private static List<Object> nextArray(JSONTokener tokener, List<Diagnostic> diagnostics, String locale, String path) {
            List<Object> array = new ArrayList<>();
            char c = tokener.nextClean();
            if (c == ']') {
                return array;
            }
            tokener.back();

            while (true) {
                array.add(nextValue(tokener, diagnostics, locale, path));
                c = tokener.nextClean();
                if (c == ']') {
                    return array;
                }
                if (c != ',') {
                    throw tokener.syntaxError("Expected ',' or ']'");
                }
            }
        }
    }
}
