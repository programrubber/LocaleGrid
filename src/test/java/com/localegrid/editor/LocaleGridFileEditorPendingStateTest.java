package com.localegrid.editor;

import com.localegrid.model.LocaleGridRow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocaleGridFileEditorPendingStateTest {
    @Test
    void addedThenDeletedRowIsNotPendingDelete() {
        LocaleGridRow row = new LocaleGridRow("login.new", false);
        row.markAdded();
        row.setDeleted(true);

        assertFalse(LocaleGridFileEditor.isPendingAddedRow(row));
        assertFalse(LocaleGridFileEditor.isPendingEditedRow(row));
        assertFalse(LocaleGridFileEditor.isPendingDeletedRow(row));
    }

    @Test
    void existingDeletedRowIsPendingDelete() {
        LocaleGridRow row = new LocaleGridRow("login.title", false);

        row.setDeleted(true);

        assertTrue(LocaleGridFileEditor.isPendingDeletedRow(row));
    }
}
