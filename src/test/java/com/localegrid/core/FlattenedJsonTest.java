package com.localegrid.core;

import com.localegrid.model.Diagnostic;
import com.localegrid.model.ExceptionKeyMarker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    @Test
    void reportsDuplicatedJsonKeysBeforeKeepingLastValue() {
        String json = """
            {
              "title": "First",
              "title": "Second"
            }
            """;
        List<Diagnostic> diagnostics = new ArrayList<>();

        assertEquals("Second", FlattenedJson.flatten(json, diagnostics, "en").get("title").getValue());

        assertEquals(1, diagnostics.size());
        assertEquals(Diagnostic.Severity.ERROR, diagnostics.get(0).getSeverity());
        assertEquals("title", diagnostics.get(0).getKey());
        assertTrue(diagnostics.get(0).getMessage().contains("duplicated JSON key"));
    }

    @Test
    void reportsDuplicatedNestedJsonKeysWithDotPath() {
        String json = """
            {
              "login": {
                "title": "First",
                "title": "Second"
              }
            }
            """;
        List<Diagnostic> diagnostics = new ArrayList<>();

        FlattenedJson.flatten(json, diagnostics, "en");

        assertEquals(1, diagnostics.size());
        assertEquals(Diagnostic.Severity.ERROR, diagnostics.get(0).getSeverity());
        assertEquals("login.title", diagnostics.get(0).getKey());
    }

    @Test
    void hidesRootExceptionKeysAndKeepsMarkerAnchor() {
        String json = """
            {
              "__section__": "login area",
              "login": {
                "title": "Login"
              },
              "__section__": "tail area"
            }
            """;
        List<Diagnostic> diagnostics = new ArrayList<>();

        FlattenedJson.Result result = FlattenedJson.flattenWithExceptionKeys(json, diagnostics, "en", Set.of("__section__"));

        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
        assertEquals(List.of("login.title"), new ArrayList<>(result.getValues().keySet()));
        assertEquals(2, result.getExceptionKeyMarkers().size());
        assertEquals(ExceptionKeyMarker.Position.BEFORE, result.getExceptionKeyMarkers().get(0).getPosition());
        assertEquals("login", result.getExceptionKeyMarkers().get(0).getAnchorRootKey());
        assertEquals(ExceptionKeyMarker.Position.AFTER, result.getExceptionKeyMarkers().get(1).getPosition());
        assertEquals("login", result.getExceptionKeyMarkers().get(1).getAnchorRootKey());
    }

    @Test
    void treatsNestedExceptionKeyAsNormalTranslationKey() {
        String json = """
            {
              "login": {
                "__section__": "Nested"
              }
            }
            """;
        List<Diagnostic> diagnostics = new ArrayList<>();

        FlattenedJson.Result result = FlattenedJson.flattenWithExceptionKeys(json, diagnostics, "en", Set.of("__section__"));

        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
        assertEquals(List.of("login.__section__"), new ArrayList<>(result.getValues().keySet()));
        assertTrue(result.getExceptionKeyMarkers().isEmpty());
    }
}
