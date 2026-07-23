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
}
