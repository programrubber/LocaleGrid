package com.localegrid.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocaleGridSettingsStateTest {
    @Test
    void localeScriptValidationDefaultsToEnabledWarning() {
        LocaleGridSettingsState state = new LocaleGridSettingsState();

        assertTrue(state.localeScriptValidationEnabled);
        assertFalse(state.isLocaleScriptViolationError());
    }

    @Test
    void localeScriptErrorSeverityIsCaseInsensitive() {
        LocaleGridSettingsState state = new LocaleGridSettingsState();
        state.localeScriptViolationSeverity = "error";

        assertTrue(state.isLocaleScriptViolationError());
    }

    @Test
    void llmSettingsDefaultValues() {
        LocaleGridSettingsState state = new LocaleGridSettingsState();

        assertFalse(state.llmEnabled);
        assertTrue(state.getNormalizedLlmEndpoint().contains("chat/completions"));
        assertTrue(state.getNormalizedLlmModel().contains("qwen3.6-27b"));
        assertTrue(state.llmTimeoutSeconds >= 10);
    }

    @Test
    void llmSettingsNormalizedGettersHandleBlank() {
        LocaleGridSettingsState state = new LocaleGridSettingsState();
        state.llmEndpoint = "   ";
        state.llmModel = "";

        assertTrue(state.getNormalizedLlmEndpoint().contains("localhost"));
        assertTrue(state.getNormalizedLlmModel().contains("qwen3.6-27b"));

        state.llmEndpoint = " https://api.custom-ai.internal/v1/chat/completions ";
        state.llmModel = " deepseek-v3 ";

        org.junit.jupiter.api.Assertions.assertEquals(
            "https://api.custom-ai.internal/v1/chat/completions",
            state.getNormalizedLlmEndpoint()
        );
        org.junit.jupiter.api.Assertions.assertEquals("deepseek-v3", state.getNormalizedLlmModel());
    }
}
