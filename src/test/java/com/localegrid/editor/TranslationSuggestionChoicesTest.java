package com.localegrid.editor;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class TranslationSuggestionChoicesTest {
    @Test
    void dismissRemovesOnlyOneAndApplyClearsOnlyThatLocalesCandidates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicReference<String> applied = new AtomicReference<>();
            AtomicReference<Boolean> visible = new AtomicReference<>(true);
            TranslationSuggestionChoices choices = choices("ko",
                List.of("로그인에 실패했습니다.", "로그인하지 못했습니다.", "로그인 실패"), applied::set, visible::set);
            TranslationSuggestionChoices other = choices("en",
                List.of("Login failed.", "Unable to log in."), value -> {}, value -> {});
            TranslationSuggestionChip dismissed = (TranslationSuggestionChip) choices.getComponent(1);
            for (Component child : dismissed.getComponents()) if (child instanceof JButton button) button.doClick();
            assertNull(applied.get());
            assertEquals(2, choices.getComponentCount());
            assertTrue(visible.get());
            TranslationSuggestionChip selected = (TranslationSuggestionChip) choices.getComponent(1);
            selected.getActionMap().get("applySuggestion").actionPerformed(null);
            assertEquals("로그인 실패", applied.get());
            assertEquals(0, choices.getComponentCount());
            assertFalse(visible.get());
            assertFalse(choices.isVisible());
            assertEquals(2, other.getComponentCount());
        });
    }

    @Test
    void wrappingReservesHeightAboveTheEditorAndShrinksWhenDismissed() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TranslationSuggestionChoices choices = choices("ko",
                List.of("로그인에 실패했습니다.", "로그인하지 못했습니다.", "로그인 실패"), text -> {}, visible -> {});
            JPanel container = new JPanel(new BorderLayout());
            container.add(choices, BorderLayout.CENTER);
            JTextArea editor = new JTextArea(2, 48);
            JLabel label = new JLabel("ko");
            label.setPreferredSize(new Dimension(44, 26));
            TranslationDetailRow row = new TranslationDetailRow(label, editor, container);
            int wideHeight = layout(row, 1000);
            int narrowHeight = layout(row, 240);
            assertTrue(narrowHeight > wideHeight);
            for (int i = 0; i < choices.getComponentCount(); i++) {
                Rectangle bounds = choices.getComponent(i).getBounds();
                assertTrue(bounds.x + bounds.width <= choices.getWidth());
                assertTrue(bounds.y + bounds.height <= choices.getHeight());
                for (int j = i + 1; j < choices.getComponentCount(); j++) {
                    assertFalse(bounds.intersects(choices.getComponent(j).getBounds()));
                }
            }
            Rectangle chips = SwingUtilities.convertRectangle(choices.getParent(), choices.getBounds(), row);
            Rectangle input = SwingUtilities.convertRectangle(editor.getParent(), editor.getBounds(), row);
            assertTrue(chips.y + chips.height <= input.y);
            TranslationSuggestionChip first = (TranslationSuggestionChip) choices.getComponent(0);
            for (Component child : first.getComponents()) if (child instanceof JButton button) button.doClick();
            assertTrue(layout(row, 240) < narrowHeight);
        });
    }

    private static TranslationSuggestionChoices choices(String locale, List<String> values,
        java.util.function.Consumer<String> onApply, java.util.function.Consumer<Boolean> onVisible) {
        java.util.Map<String, String> candidates = new java.util.LinkedHashMap<>();
        for (int i = 0; i < values.size(); i++) candidates.put(String.valueOf((char) ('A' + i)), values.get(i));
        return new TranslationSuggestionChoices(locale, candidates, (id, text) -> onApply.accept(text), onVisible);
    }

    @Test
    void selectingBRetainsBInOtherLocalesWithoutApplyingTheirValues() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var sets = List.of(
                new com.localegrid.llm.TranslationSuggestionSet("A", java.util.Map.of("ko", "한국어 A", "en", "English A", "ja", "共通")),
                new com.localegrid.llm.TranslationSuggestionSet("B", java.util.Map.of("ko", "한국어 B", "en", "English B", "ja", "共通")));
            var selection = new TranslationSuggestionSelection(sets);
            AtomicReference<String> koValue = new AtomicReference<>();
            AtomicReference<String> enValue = new AtomicReference<>();
            AtomicReference<String> jaValue = new AtomicReference<>();
            var ko = selection.addLocale("ko", koValue::set, visible -> {});
            var en = selection.addLocale("en", enValue::set, visible -> {});
            var ja = selection.addLocale("ja", jaValue::set, visible -> {});
            // Dismiss A locally. B must keep its identity rather than becoming the new A.
            var dismissed = (TranslationSuggestionChip) ko.getComponent(0);
            for (Component child : dismissed.getComponents()) if (child instanceof JButton button) button.doClick();
            assertEquals(2, en.getComponentCount());
            var selected = (TranslationSuggestionChip) ko.getComponent(0);
            assertTrue(((JLabel) selected.getComponent(0)).getText().startsWith("[B안]"));
            selected.getActionMap().get("applySuggestion").actionPerformed(null);
            assertEquals("한국어 B", koValue.get());
            assertNull(enValue.get());
            assertNull(jaValue.get());
            assertEquals(0, ko.getComponentCount());
            for (var panel : List.of(en, ja)) {
                assertEquals(1, panel.getComponentCount());
                assertEquals("B", ((JComponent) panel.getComponent(0)).getClientProperty("candidateId"));
            }
            ((TranslationSuggestionChip) en.getComponent(0)).getActionMap().get("applySuggestion").actionPerformed(null);
            assertEquals("English B", enValue.get());
            assertNull(jaValue.get());
            assertEquals(1, ja.getComponentCount());
            var nextRequest = new TranslationSuggestionSelection(sets);
            assertEquals(2, nextRequest.addLocale("ko", text -> {}, visible -> {}).getComponentCount());
        });
    }

    private static int layout(TranslationDetailRow row, int width) {
        row.setSize(width, 1);
        row.setSize(width, row.getPreferredSize().height);
        layoutChildren(row);
        return row.getHeight();
    }

    private static void layoutChildren(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container nested) layoutChildren(nested);
    }
}
