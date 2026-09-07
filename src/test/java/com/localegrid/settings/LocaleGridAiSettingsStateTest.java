package com.localegrid.settings;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocaleGridAiSettingsStateTest {
    @Test
    void llmSettingsDefaultValues() {
        LocaleGridAiSettingsState state = new LocaleGridAiSettingsState();

        assertFalse(state.llmEnabled);
        assertTrue(state.getNormalizedLlmEndpoint().contains("chat/completions"));
        assertTrue(state.getNormalizedLlmModel().contains("qwen3.6-27b"));
        assertTrue(state.llmTimeoutSeconds >= 10);
    }

    @Test
    void llmSettingsNormalizedGettersHandleBlank() {
        LocaleGridAiSettingsState state = new LocaleGridAiSettingsState();
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

    @Test
    void defaultProjectDoesNotPreventLaterMigration() {
        LocaleGridAiSettingsState global = new LocaleGridAiSettingsState();
        global.migrateFrom(new LocaleGridSettingsState());
        assertFalse(global.initialized);

        LocaleGridSettingsState legacy = new LocaleGridSettingsState();
        legacy.llmEnabled = true;
        legacy.llmEndpoint = "https://ai.example.test/v1/chat/completions";
        legacy.llmModel = "company-model";
        legacy.llmApiKey = "test-token";
        legacy.llmTimeoutSeconds = 120;
        legacy.llmTemperature = 0.7;
        global.migrateFrom(legacy);

        assertTrue(global.initialized);
        assertTrue(global.llmEnabled);
        assertEquals(legacy.llmEndpoint, global.llmEndpoint);
        assertEquals(legacy.llmModel, global.llmModel);
        assertEquals(legacy.llmApiKey, global.llmApiKey);
        assertEquals(120, global.llmTimeoutSeconds);
        assertEquals(0.7, global.llmTemperature);
    }

    @Test
    void anotherProjectCannotOverwriteMigratedSettings() {
        LocaleGridAiSettingsState global = new LocaleGridAiSettingsState();
        LocaleGridSettingsState first = new LocaleGridSettingsState();
        first.llmModel = "first-model";
        global.migrateFrom(first);
        LocaleGridSettingsState second = new LocaleGridSettingsState();
        second.llmModel = "second-model";
        global.migrateFrom(second);
        assertEquals("first-model", global.llmModel);
    }

    @Test
    void explicitlySavedDefaultsAreNotOverwrittenAfterReload() {
        LocaleGridAiSettingsState saved = new LocaleGridAiSettingsState();
        saved.initialized = true;
        LocaleGridAiSettingsState reloaded = new LocaleGridAiSettingsState();
        reloaded.loadState(saved);
        LocaleGridSettingsState legacy = new LocaleGridSettingsState();
        legacy.llmEnabled = true;
        reloaded.migrateFrom(legacy);
        assertTrue(reloaded.initialized);
        assertFalse(reloaded.llmEnabled);
    }

    @Test
    void disabledButCustomizedConnectionIsMigratedWithoutEnablingIt() {
        LocaleGridAiSettingsState global = new LocaleGridAiSettingsState();
        LocaleGridSettingsState legacy = new LocaleGridSettingsState();
        legacy.llmModel = "disabled-model";
        global.migrateFrom(legacy);
        assertTrue(global.initialized);
        assertFalse(global.llmEnabled);
        assertEquals("disabled-model", global.llmModel);
    }
}
