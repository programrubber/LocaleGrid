package com.localegrid.editor;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;

class AiSuggestionHeaderTest {
    @Test
    void stylesAreExclusiveTemporaryAndDisabledWhileRequesting() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AiSuggestionHeader header = new AiSuggestionHeader(new JLabel("login.forgotPassword"), new JButton("AI 번역 제안"));
            assertEquals(com.localegrid.llm.TranslationStyle.PRESERVE, header.selectedStyle());
            JPanel controls = (JPanel) header.getComponent(0);
            JPanel styles = (JPanel) controls.getComponent(1);
            assertFalse(styles.isVisible());
            header.setStylesAvailable(true, true);
            controls.getComponent(2).setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
            for (Component radio : styles.getComponents()) assertEquals(12, radio.getFont().getSize());
            JToggleButton label = (JToggleButton) styles.getComponent(1);
            label.doClick();
            assertEquals(com.localegrid.llm.TranslationStyle.LABEL, header.selectedStyle());
            assertFalse(((JToggleButton) styles.getComponent(0)).isSelected());
            header.setStylesAvailable(true, false);
            ((JToggleButton) styles.getComponent(2)).doClick();
            assertEquals(com.localegrid.llm.TranslationStyle.LABEL, header.selectedStyle());
            for (Component radio : styles.getComponents()) assertFalse(radio.isEnabled());
            header.setStylesAvailable(false, true);
            header.setStylesAvailable(true, true);
            assertEquals(com.localegrid.llm.TranslationStyle.LABEL, header.selectedStyle());
            for (int width : new int[]{1000, 600, 480}) {
                header.setSize(width, 70);
                header.doLayout();
                Rectangle styleBounds = styles.getBounds();
                Rectangle buttonBounds = controls.getComponent(2).getBounds();
                assertFalse(styleBounds.intersects(buttonBounds));
                assertTrue(buttonBounds.x + buttonBounds.width <= width);
                assertFalse(controls.getComponent(0).getBounds().intersects(styleBounds));
            }
            AiSuggestionHeader reopened = new AiSuggestionHeader(new JLabel("key"), new JButton("AI 번역 제안"));
            assertEquals(com.localegrid.llm.TranslationStyle.PRESERVE, reopened.selectedStyle());
        });
    }

    @Test
    void aiFeedbackNeverReplacesTableStatusOrItsColor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JLabel table = new JLabel("카테고리: login | Row: 8 | 편집: 1 | 에러: 0, 경고: 2");
            table.setForeground(Color.GRAY);
            String original = table.getText();
            JButton button = new AiTranslationButton("AI 번역 제안", new AiTranslationButton.SparkleIcon(), true);
            AiSuggestionHeader panel = new AiSuggestionHeader(new JLabel("login.forgotPassword"), button);
            for (String message : new String[]{"생성 중", "2개 언어의 번역 제안", "추천된 번역 문구가 없습니다.", "AI 제안 실패"}) {
                panel.showAiStatus(message, message.contains("실패"));
                assertEquals(original, table.getText());
                assertEquals(Color.GRAY, table.getForeground());
                assertTrue(table.isVisible());
                JLabel ai = (JLabel) panel.getComponent(1);
                assertEquals(button.getFont(), ai.getFont());
                assertEquals(message, ai.getText());
                assertEquals(message, ai.getToolTipText());
                panel.setSize(800, 28);
                panel.doLayout();
                ((Container) panel.getComponent(0)).doLayout();
                Rectangle bounds = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), panel);
                assertTrue(ai.getX() > bounds.x + bounds.width);
                assertEquals(10, ai.getX() - bounds.x - bounds.width);
                assertEquals(bounds.getCenterY(), ai.getBounds().getCenterY(), 1);
                assertEquals(panel.getHeight(), ai.getHeight());
                assertNull(table.getParent());
            }
            panel.clearAiStatus();
            assertEquals(original, table.getText());
            assertFalse(panel.getComponent(1).isVisible());
        });
    }
}
