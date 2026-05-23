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

    private static void assertOrder(String text, String first, String second, String third) {
        assertTrue(text.indexOf(first) < text.indexOf(second), text);
        assertTrue(text.indexOf(second) < text.indexOf(third), text);
    }

    private static void assertOrder(String text, String first, String second) {
        assertTrue(text.indexOf(first) < text.indexOf(second), text);
    }
}
