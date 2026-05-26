package com.localegrid.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocaleTextEscaperTest {
    @Test
    void escapesControlCharactersForEditorDisplay() {
        String text = "line1\nline2\tend\rnext";

        assertEquals("line1\\nline2\\tend\\rnext", LocaleTextEscaper.escapeForEditor(text));
    }

    @Test
    void unescapesEditorTextBackToJsonStringMeaning() {
        String text = "line1\\nline2\\tend\\rnext";

        assertEquals("line1\nline2\tend\rnext", LocaleTextEscaper.unescapeFromEditor(text));
    }

    @Test
    void keepsLiteralBackslashSequencesRoundTrippable() {
        String text = "literal \\n path C:\\temp";

        assertEquals(text, LocaleTextEscaper.unescapeFromEditor(LocaleTextEscaper.escapeForEditor(text)));
    }
}
