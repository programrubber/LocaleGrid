package com.localegrid.core;

import com.localegrid.model.Diagnostic;
import com.localegrid.model.LocaleValue;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

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
            Object parsed = new JSONTokener(json).nextValue();
            if (!(parsed instanceof JSONObject)) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, locale + " JSON root must be an object.", null));
                return values;
            }
            flattenObject("", (JSONObject) parsed, values, diagnostics, locale);
        } catch (JSONException ex) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, locale + " JSON parse failed: " + ex.getMessage(), null));
        }
        return values;
    }

    private static void flattenObject(
        String prefix,
        JSONObject object,
        Map<String, LocaleValue> values,
        List<Diagnostic> diagnostics,
        String locale
    ) {
        for (String name : object.keySet()) {
            Object value = object.opt(name);
            String key = prefix.isEmpty() ? name : prefix + "." + name;
            if (value instanceof JSONObject) {
                flattenObject(key, (JSONObject) value, values, diagnostics, locale);
            } else {
                if (values.containsKey(key)) {
                    diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, locale + " has duplicated dot path: " + key, key));
                }
                boolean editable = value instanceof String || value == JSONObject.NULL || value == null;
                if (value instanceof JSONArray) {
                    editable = false;
                }
                values.put(key, new LocaleValue(value == JSONObject.NULL ? "" : value, true, editable));
            }
        }
    }
}
