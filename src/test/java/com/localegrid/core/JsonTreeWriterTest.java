package com.localegrid.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTreeWriterTest {
    @Test
    void preservesMixedRootKeyOrder() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", "Login");
        values.put("button.submit", "Sign in");
        values.put("message", "Welcome");

        String json = JsonTreeWriter.write(values, 2, new ArrayList<>());

        assertOrder(json, "\"title\"", "\"button\"", "\"message\"");
    }

    @Test
    void preservesMixedNestedKeyOrder() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("login.title", "Login");
        values.put("login.button.submit", "Sign in");
        values.put("login.message", "Welcome");

        String json = JsonTreeWriter.write(values, 2, new ArrayList<>());

        assertOrder(json, "\"title\"", "\"button\"", "\"message\"");
    }

    @Test
    void writesNestedReadonlyValuesWithoutReorderingObjectFields() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("first", 1);
        nested.put("second", true);
        List<Object> array = List.of(nested);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("items", array);

        String json = JsonTreeWriter.write(values, 2, new ArrayList<>());

        assertOrder(json, "\"first\"", "\"second\"");
    }

    @Test
    void writesDuplicatedRootEntriesWhenRootEntryListIsUsed() {
        List<JsonRootEntry> entries = List.of(
            new JsonRootEntry("__section__", "first"),
            new JsonRootEntry("login", Map.of("title", "Login")),
            new JsonRootEntry("__section__", "second")
        );

        String json = JsonTreeWriter.writeRootEntries(entries, 2, new ArrayList<>());

        assertTrue(json.indexOf("\"__section__\"") < json.indexOf("\"login\""), json);
        assertTrue(json.indexOf("\"login\"") < json.lastIndexOf("\"__section__\""), json);
    }

    private static void assertOrder(String text, String first, String second, String third) {
        assertTrue(text.indexOf(first) < text.indexOf(second), text);
        assertTrue(text.indexOf(second) < text.indexOf(third), text);
    }

    private static void assertOrder(String text, String first, String second) {
        assertTrue(text.indexOf(first) < text.indexOf(second), text);
    }
}
