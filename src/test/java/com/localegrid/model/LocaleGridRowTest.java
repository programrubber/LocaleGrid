package com.localegrid.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocaleGridRowTest {
    @Test
    void newlyAddedRowIsModifiedEvenBeforeValuesAreEntered() {
        LocaleGridRow row = new LocaleGridRow("login.help", false);

        row.markAdded();

        assertTrue(row.isAdded());
        assertTrue(row.isModified());
    }

    @Test
    void renamedRowIsModifiedEvenWhenValuesAreUnchanged() {
        LocaleGridRow row = new LocaleGridRow("login.title", false);

        row.rename("login.heading");

        assertTrue(row.isModified());
    }
}
