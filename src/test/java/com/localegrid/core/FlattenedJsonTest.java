package com.localegrid.core;

import com.localegrid.model.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlattenedJsonTest {
    @Test
    void preservesSourceJsonKeyOrderWhenFlattening() {
        String json = """
            {
              "title": "Login",
              "button": {
                "submit": "Sign in"
              },
              "message": "Welcome"
            }
            """;
        List<Diagnostic> diagnostics = new ArrayList<>();

        List<String> keys = new ArrayList<>(FlattenedJson.flatten(json, diagnostics, "en").keySet());

        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
        assertEquals(List.of("title", "button.submit", "message"), keys);
    }

    @Test
    void preservesNestedObjectKeyOrderWhenFlattening() {
        String json = """
            {
              "login": {
                "title": "Login",
                "button": {
                  "submit": "Sign in"
                },
                "message": "Welcome"
              },
              "help": "Forgot password?"
            }
            """;
        List<Diagnostic> diagnostics = new ArrayList<>();

        List<String> keys = new ArrayList<>(FlattenedJson.flatten(json, diagnostics, "en").keySet());

        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
        assertEquals(List.of("login.title", "login.button.submit", "login.message", "help"), keys);
    }
}
