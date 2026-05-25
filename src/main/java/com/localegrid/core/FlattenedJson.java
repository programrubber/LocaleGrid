package com.localegrid.core;

import com.localegrid.model.Diagnostic;
import com.localegrid.model.LocaleValue;
import com.localegrid.model.ExceptionKeyMarker;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FlattenedJson {
    private FlattenedJson() {
    }

    public static Map<String, LocaleValue> flatten(String content, List<Diagnostic> diagnostics, String locale) {
        return flattenWithExceptionKeys(content, diagnostics, locale, Set.of()).getValues();
    }

    public static Result flattenWithExceptionKeys(
        String content,
        List<Diagnostic> diagnostics,
        String locale,
        Set<String> exceptionKeys
    ) {
        Result result = new Result();
        String json = content == null || content.trim().isEmpty() ? "{}" : content;
        Set<String> duplicatedJsonKeys = new HashSet<>();
        try {
            Object parsed = OrderedParser.parse(json, diagnostics, locale, exceptionKeys, duplicatedJsonKeys);
            if (!(parsed instanceof ParsedObject)) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, locale + " JSON root must be an object.", null));
                return result;
            }
            flattenRoot(asObject(parsed), result, diagnostics, locale, exceptionKeys, duplicatedJsonKeys);
        } catch (JSONException ex) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, locale + " JSON parse failed: " + ex.getMessage(), null));
        }
        return result;
    }

    private static void flattenRoot(
        ParsedObject root,
        Result result,
        List<Diagnostic> diagnostics,
        String locale,
        Set<String> exceptionKeys,
        Set<String> duplicatedJsonKeys
    ) {
        for (int i = 0; i < root.members.size(); i++) {
            Member member = root.members.get(i);
            if (exceptionKeys.contains(member.key)) {
                ExceptionKeyMarker marker = createExceptionKeyMarker(root.members, i);
                if (marker != null) {
                    result.exceptionKeyMarkers.add(marker);
                }
                continue;
            }

            if (member.value instanceof ParsedObject) {
                flattenObject(member.key, asObject(member.value), result.values, diagnostics, locale, duplicatedJsonKeys);
            } else {
                putValue(member.key, member.value, result.values, diagnostics, locale, duplicatedJsonKeys);
            }
        }
    }

    private static ExceptionKeyMarker createExceptionKeyMarker(List<Member> members, int exceptionKeyIndex) {
        Member exceptionMember = members.get(exceptionKeyIndex);
        for (int i = exceptionKeyIndex + 1; i < members.size(); i++) {
            Member next = members.get(i);
            if (!next.exceptionKey) {
                return new ExceptionKeyMarker(exceptionMember.key, unwrap(exceptionMember.value), next.key, ExceptionKeyMarker.Position.BEFORE);
            }
        }
        for (int i = exceptionKeyIndex - 1; i >= 0; i--) {
            Member previous = members.get(i);
            if (!previous.exceptionKey) {
                return new ExceptionKeyMarker(exceptionMember.key, unwrap(exceptionMember.value), previous.key, ExceptionKeyMarker.Position.AFTER);
            }
        }
        return null;
    }

    private static void flattenObject(
        String prefix,
        ParsedObject object,
        Map<String, LocaleValue> values,
        List<Diagnostic> diagnostics,
        String locale,
        Set<String> duplicatedJsonKeys
    ) {
        for (Member member : object.members) {
            String key = prefix.isEmpty() ? member.key : prefix + "." + member.key;
            if (member.value instanceof ParsedObject) {
                flattenObject(key, asObject(member.value), values, diagnostics, locale, duplicatedJsonKeys);
            } else {
                putValue(key, member.value, values, diagnostics, locale, duplicatedJsonKeys);
            }
        }
    }

    private static void putValue(
        String key,
        Object value,
        Map<String, LocaleValue> values,
        List<Diagnostic> diagnostics,
        String locale,
        Set<String> duplicatedJsonKeys
    ) {
        if (values.containsKey(key) && !duplicatedJsonKeys.contains(key)) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, locale + " has duplicated dot path: " + key, key));
        }
        Object unwrapped = unwrap(value);
        boolean editable = unwrapped instanceof String || unwrapped == JSONObject.NULL || unwrapped == null;
        if (unwrapped instanceof List || unwrapped instanceof JSONArray) {
            editable = false;
        }
        values.put(key, new LocaleValue(unwrapped == JSONObject.NULL ? "" : unwrapped, true, editable));
    }

    @SuppressWarnings("unchecked")
    private static ParsedObject asObject(Object value) {
        return (ParsedObject) value;
    }

    private static Object unwrap(Object value) {
        if (value instanceof ParsedObject) {
            Map<String, Object> object = new LinkedHashMap<>();
            for (Member member : asObject(value).members) {
                object.put(member.key, unwrap(member.value));
            }
            return object;
        }
        if (value instanceof List) {
            List<Object> list = new ArrayList<>();
            for (Object item : (List<?>) value) {
                list.add(unwrap(item));
            }
            return list;
        }
        return value;
    }

    public static final class Result {
        private final Map<String, LocaleValue> values = new LinkedHashMap<>();
        private final List<ExceptionKeyMarker> exceptionKeyMarkers = new ArrayList<>();

        public Map<String, LocaleValue> getValues() {
            return values;
        }

        public List<ExceptionKeyMarker> getExceptionKeyMarkers() {
            return exceptionKeyMarkers;
        }
    }

    private static final class Member {
        private final String key;
        private final Object value;
        private final boolean exceptionKey;

        private Member(String key, Object value, boolean exceptionKey) {
            this.key = key;
            this.value = value;
            this.exceptionKey = exceptionKey;
        }
    }

    private static final class ParsedObject {
        private final List<Member> members = new ArrayList<>();
    }

    private static final class OrderedParser {
        private OrderedParser() {
        }

        private static Object parse(
            String json,
            List<Diagnostic> diagnostics,
            String locale,
            Set<String> exceptionKeys,
            Set<String> duplicatedJsonKeys
        ) {
            JSONTokener tokener = new JSONTokener(json);
            Object value = nextValue(tokener, diagnostics, locale, "", exceptionKeys, duplicatedJsonKeys);
            if (tokener.nextClean() != 0) {
                throw tokener.syntaxError("Unexpected trailing content");
            }
            return value;
        }

        private static Object nextValue(
            JSONTokener tokener,
            List<Diagnostic> diagnostics,
            String locale,
            String path,
            Set<String> exceptionKeys,
            Set<String> duplicatedJsonKeys
        ) {
            char c = tokener.nextClean();
            switch (c) {
                case '"':
                case '\'':
                    return tokener.nextString(c);
                case '{':
                    return nextObject(tokener, diagnostics, locale, path, exceptionKeys, duplicatedJsonKeys);
                case '[':
                    return nextArray(tokener, diagnostics, locale, path, exceptionKeys, duplicatedJsonKeys);
                default:
                    tokener.back();
                    return tokener.nextValue();
            }
        }

        private static ParsedObject nextObject(
            JSONTokener tokener,
            List<Diagnostic> diagnostics,
            String locale,
            String path,
            Set<String> exceptionKeys,
            Set<String> duplicatedJsonKeys
        ) {
            ParsedObject object = new ParsedObject();
            Set<String> seen = new LinkedHashSet<>();
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
                boolean rootExceptionKey = path.isEmpty() && exceptionKeys.contains(key);
                if (!seen.add(key) && !rootExceptionKey) {
                    duplicatedJsonKeys.add(childPath);
                    diagnostics.add(new Diagnostic(
                        Diagnostic.Severity.ERROR,
                        locale + " has duplicated JSON key: " + childPath,
                        childPath
                    ));
                }
                object.members.add(new Member(
                    key,
                    nextValue(tokener, diagnostics, locale, childPath, exceptionKeys, duplicatedJsonKeys),
                    rootExceptionKey
                ));

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

        private static List<Object> nextArray(
            JSONTokener tokener,
            List<Diagnostic> diagnostics,
            String locale,
            String path,
            Set<String> exceptionKeys,
            Set<String> duplicatedJsonKeys
        ) {
            List<Object> array = new ArrayList<>();
            char c = tokener.nextClean();
            if (c == ']') {
                return array;
            }
            tokener.back();

            while (true) {
                array.add(nextValue(tokener, diagnostics, locale, path, exceptionKeys, duplicatedJsonKeys));
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
