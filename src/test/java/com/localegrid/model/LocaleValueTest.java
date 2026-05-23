package com.localegrid.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocaleValueTest {
    @Test
    void editedMissingValueIsVisibleButStillTrackedAsAdded() {
        LocaleValue value = LocaleValue.missing();

        value.setText("Message!!");

        assertEquals("Message!!", value.getDisplayText());
        assertEquals("Message!!", value.getValue());
        assertFalse(value.isPresent());
        assertTrue(value.isModified());
    }
}
