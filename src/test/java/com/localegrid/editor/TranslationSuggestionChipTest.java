package com.localegrid.editor;

import org.junit.jupiter.api.Test;

import com.intellij.ui.JBColor;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TranslationSuggestionChipTest {

    @Test
    void chipStoresLocaleAndSuggestedText() {
        TranslationSuggestionChip chip = new TranslationSuggestionChip(
            "ja",
            "こんにちは",
            text -> {},
            () -> {}
        );

        assertEquals("ja", chip.getTargetLocale());
        assertEquals("こんにちは", chip.getSuggestedText());
    }

    @Test
    void chipCallbacksWork() throws Exception {
        AtomicReference<String> applied = new AtomicReference<>();
        AtomicBoolean dismissed = new AtomicBoolean(false);

        TranslationSuggestionChip chip = new TranslationSuggestionChip(
            "vi",
            "Xin chào",
            applied::set,
            () -> dismissed.set(true)
        );

        SwingUtilities.invokeAndWait(() -> {
            List<JButton> buttons = buttons(chip);
            assertEquals(1, buttons.size());
            assertNull(applied.get());
            assertFalse(dismissed.get());
            click(chip);
            assertEquals("Xin chào", applied.get());
            assertFalse(dismissed.get());
            applied.set(null);
            click(chip.getComponent(0));
            assertEquals("Xin chào", applied.get());
            applied.set(null);
            chip.getActionMap().get("applySuggestion").actionPerformed(
                new java.awt.event.ActionEvent(chip, 0, "applySuggestion"));
            assertEquals("Xin chào", applied.get());
            applied.set(null);
            buttons.get(0).doClick();
            assertTrue(dismissed.get());
            assertNull(applied.get());
        });
    }

    private static void click(Component component) {
        component.dispatchEvent(new java.awt.event.MouseEvent(component, java.awt.event.MouseEvent.MOUSE_CLICKED,
            System.currentTimeMillis(), 0, 2, 2, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }

    @Test
    void shortSuggestionStaysCompact() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TranslationSuggestionChip chip = new TranslationSuggestionChip("ko", "비밀번호를 잊으셨나요?", null, null);
            assertEquals(com.intellij.util.ui.JBUI.scale(28), sizeAndLayout(chip, 850));
            assertEquals(com.intellij.util.ui.JBUI.scale(28), sizeAndLayout(chip, 300));
            assertEquals(11f, ((JLabel) chip.getComponent(0)).getFont().getSize2D());
            assertTrue(chip.getPreferredSize().width < 300);
            JLabel icon = (JLabel) chip.getComponent(1);
            assertNotNull(icon.getIcon());
            assertTrue(icon.getX() < chip.getComponent(0).getX());
            assertInside(chip);
        });
    }

    @Test
    void longSuggestionsStaySingleLineWithFullTooltip() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String text = "비밀번호를 잊으셨나요? 계정에 등록된 이메일 주소를 입력하시면 비밀번호를 재설정할 수 있는 안내 메일을 보내드립니다. ".repeat(3);
            TranslationSuggestionChip chip = new TranslationSuggestionChip("ko", text, null, null);
            JPanel host = new JPanel(new BorderLayout());
            host.add(chip, BorderLayout.WEST);
            host.setSize(360, 28);
            int wideHeight = sizeAndLayout(chip, chip.getPreferredSize().width);
            int narrowHeight = sizeAndLayout(chip, 280);
            assertEquals(wideHeight, narrowHeight);
            assertEquals(360, chip.getPreferredSize().width);
            JLabel preview = (JLabel) chip.getComponent(0);
            assertEquals(text, preview.getText());
            assertTrue(preview.getToolTipText().contains(text));
            assertEquals(chip.getToolTipText(), preview.getToolTipText());
            String displayed = SwingUtilities.layoutCompoundLabel(preview, preview.getFontMetrics(preview.getFont()),
                preview.getText(), null, SwingConstants.CENTER, SwingConstants.LEFT,
                SwingConstants.CENTER, SwingConstants.RIGHT, new Rectangle(0, 0, preview.getWidth(), preview.getHeight()),
                new Rectangle(), new Rectangle(), 0);
            assertTrue(displayed.endsWith("..."));
            assertInside(chip);
            assertEquals(wideHeight, sizeAndLayout(chip, 850));
            assertInside(chip);
        });
    }

    @Test
    void tooltipEscapesMarkupAndKeepsFullSuggestionForApply() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String text = "<html><b>{name}</b> &\n다음 줄";
            AtomicReference<String> applied = new AtomicReference<>();
            TranslationSuggestionChip chip = new TranslationSuggestionChip("ko", text, applied::set, null);
            assertTrue(chip.getToolTipText().contains("&lt;html&gt;&lt;b&gt;{name}&lt;/b&gt; &amp;<br>"));
            assertEquals(Boolean.TRUE, ((JLabel) chip.getComponent(0)).getClientProperty("html.disable"));
            click(chip);
            assertEquals(text, applied.get());
        });
    }

    @Test
    void rowsKeepInputVisibleWithSuggestions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel rows = rows();
            for (int width : new int[]{900, 350, 900}) {
                rows.setSize(width, 1200);
                for (int pass = 0; pass < 3; pass++) layout(rows);
                for (Component row : rows.getComponents()) {
                    assertTrue(row.getHeight() >= row.getPreferredSize().height);
                    assertInside((Container) row);
                }
            }
        });
    }

    @Test
    void shortViewportScrollsInsteadOfShrinkingEditors() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel rows = rows();
            JScrollPane scroll = new JScrollPane(rows);
            scroll.setSize(350, 160);
            for (int pass = 0; pass < 4; pass++) layout(scroll);
            assertTrue(scroll.getVerticalScrollBar().isVisible());
            assertFalse(scroll.getHorizontalScrollBar().isVisible());
            assertTrue(rows.getHeight() > scroll.getViewport().getHeight());
            for (Component row : rows.getComponents()) {
                assertEquals(row.getPreferredSize().height, row.getHeight());
                assertInside((Container) row);
            }
        });
    }

    @Test
    void renderThemePreviewsWhenRequested() throws Exception {
        String output = System.getenv("LOCALEGRID_UI_PREVIEW_DIR");
        if (output == null) return;
        SwingUtilities.invokeAndWait(() -> {
            boolean wasDark = !JBColor.isBright();
            try {
                for (boolean dark : new boolean[]{true, false}) {
                    JBColor.setDark(dark);
                    for (int width : new int[]{960, 390}) {
                        JPanel root = new JPanel(new BorderLayout(0, 16));
                        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
                        root.setBackground(dark ? new Color(43, 45, 48) : new Color(250, 250, 252));
                        JLabel title = new JLabel("login.forgotPassword");
                        title.setForeground(dark ? new Color(220, 222, 228) : new Color(48, 50, 57));
                        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
                        AiSuggestionHeader header = new AiSuggestionHeader(title,
                            new AiTranslationButton("AI 번역 제안", new AiTranslationButton.SparkleIcon(), true));
                        header.showAiStatus("2개 언어의 번역 제안이 표시되었습니다. 칩을 클릭하면 적용됩니다.", false);
                        root.add(header, BorderLayout.NORTH);
                        JPanel rows = rows();
                        root.add(rows, BorderLayout.CENTER);
                        JLabel status = new JLabel("카테고리: login | Row: 8 | 편집: 0 | 에러: 0, 경고: 2");
                        status.setForeground(title.getForeground());
                        status.setFont(status.getFont().deriveFont(Font.PLAIN, 11f));
                        root.add(status, BorderLayout.SOUTH);
                        root.setSize(width, width < 500 ? 640 : 430);
                        for (int pass = 0; pass < 4; pass++) layout(root);
                        BufferedImage image = new BufferedImage(width * 2, root.getHeight() * 2, BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics = image.createGraphics();
                        graphics.scale(2, 2);
                        root.printAll(graphics);
                        graphics.dispose();
                        File target = new File(output, (dark ? "dark" : "light") + "-" + width + ".png");
                        target.getParentFile().mkdirs();
                        try { ImageIO.write(image, "png", target); }
                        catch (java.io.IOException exception) { throw new RuntimeException(exception); }
                    }
                }
            } finally { JBColor.setDark(wasDark); }
        });
    }

    private static JPanel rows() {
        JPanel rows = new TranslationDetailFields();
        rows.setOpaque(false);
        for (String locale : new String[]{"ko", "en", "ja"}) {
            JLabel label = new JLabel(locale);
            label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
            label.setForeground(new JBColor(new Color(88, 90, 96), new Color(160, 163, 173)));
            label.setPreferredSize(new Dimension(32, 26));
            JTextArea input = new JTextArea(locale.equals("en") ? "Forgot password?" : "", 2, 48);
            input.setFont(input.getFont().deriveFont(Font.PLAIN, 13f));
            input.setBackground(new JBColor(Color.WHITE, new Color(35, 37, 40)));
            input.setForeground(new JBColor(new Color(48, 50, 57), new Color(220, 222, 228)));
            JPanel suggestions = new JPanel(new BorderLayout());
            suggestions.setOpaque(false);
            if (!locale.equals("en")) suggestions.add(new TranslationSuggestionChip(locale,
                locale.equals("ko") ? "비밀번호를 잊으셨나요?" : "パスワードをお忘れですか？登録したメールアドレスに再設定のご案内をお送りします。", null, null), BorderLayout.WEST);
            suggestions.setVisible(!locale.equals("en"));
            rows.add(new TranslationDetailRow(label, input, suggestions));
        }
        return rows;
    }

    private static int sizeAndLayout(JComponent component, int width) {
        component.setSize(width, 1);
        component.setSize(width, component.getPreferredSize().height);
        layout(component);
        return component.getHeight();
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container);
    }

    private static void assertInside(Container parent) {
        for (Component child : parent.getComponents()) {
            if (!child.isVisible()) continue;
            assertTrue(child.getX() >= 0 && child.getY() >= 0, child.getClass().getSimpleName());
            assertTrue(child.getX() + child.getWidth() <= parent.getWidth(), child.getClass().getSimpleName());
            assertTrue(child.getY() + child.getHeight() <= parent.getHeight(), child.getClass().getSimpleName());
            // Scroll pane views intentionally extend beyond their viewport.
            if (child instanceof Container container && !(child instanceof JScrollPane)) assertInside(container);
        }
    }

    private static List<JButton> buttons(Container parent) {
        List<JButton> result = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton button) result.add(button);
            else if (child instanceof Container container) result.addAll(buttons(container));
        }
        return result;
    }
}
