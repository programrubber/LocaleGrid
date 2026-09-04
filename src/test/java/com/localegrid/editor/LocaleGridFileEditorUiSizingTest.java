package com.localegrid.editor;

import com.intellij.util.ui.JBUI;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocaleGridFileEditorUiSizingTest {
    @Test
    void toolbarButtonsKeepFullLabelsWithLargeFontAndIcon() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertFullLabel("삭제 취소", 104, 8);
            assertFullLabel("예외키", 84, 4);
            assertFullLabel("설정", 64, 4);
        });
    }

    @Test
    void rowActionButtonsKeepOneFixedWidthWithoutClipping() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertFixedWidthLabel("추가");
            assertFixedWidthLabel("편집");
            assertFixedWidthLabel("삭제");
        });
    }

    @Test
    void tableTracksViewportOnlyWhenPreferredColumnsFit() {
        assertTrue(LocaleGridFileEditor.shouldTrackViewportWidth(626, 1200));
        assertTrue(LocaleGridFileEditor.shouldTrackViewportWidth(626, 626));
        assertFalse(LocaleGridFileEditor.shouldTrackViewportWidth(900, 626));
        assertFalse(LocaleGridFileEditor.shouldTrackViewportWidth(626, 0));
    }

    private static void assertFullLabel(String text, int minimumWidth, int horizontalMargin) {
        LocaleGridFileEditor.ToolbarTextButton button = new LocaleGridFileEditor.ToolbarTextButton(
            text,
            new FixedIcon(24, 24),
            minimumWidth,
            horizontalMargin
        );
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 18f));

        Dimension preferredSize = button.getPreferredSize();
        button.setSize(preferredSize);

        assertEquals(text, laidOutText(button));
        assertTrue(button.getMinimumSize().width >= preferredSize.width);
        assertTrue(button.getMinimumSize().height >= preferredSize.height);
    }

    private static void assertFixedWidthLabel(String text) {
        FixedIcon icon = new FixedIcon(24, 24);
        LocaleGridFileEditor.ToolbarTextButton button = LocaleGridFileEditor.ToolbarTextButton.fixedWidth(
            text,
            icon,
            LocaleGridFileEditor.ROW_ACTION_BUTTON_WIDTH
        );
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 18f));

        LocaleGridFileEditor.ToolbarTextButton contentSizedButton = new LocaleGridFileEditor.ToolbarTextButton(
            text,
            icon,
            0,
            8
        );
        contentSizedButton.setFont(contentSizedButton.getFont().deriveFont(Font.PLAIN, 18f));

        Dimension fixedSize = button.getPreferredSize();
        JPanel panel = new JPanel(new FlowLayout());
        panel.add(button);
        panel.setSize(400, 80);
        panel.doLayout();

        int expectedWidth = JBUI.scale(LocaleGridFileEditor.ROW_ACTION_BUTTON_WIDTH);
        assertEquals(expectedWidth, fixedSize.width);
        assertEquals(expectedWidth, button.getWidth());
        assertTrue(
            fixedSize.width >= contentSizedButton.getPreferredSize().width,
            "fixed=" + fixedSize.width + ", required=" + contentSizedButton.getPreferredSize().width
        );
        assertEquals(text, laidOutText(button));
        assertEquals(expectedWidth, button.getMinimumSize().width);
        assertEquals(expectedWidth, button.getMaximumSize().width);
    }

    private static String laidOutText(AbstractButton button) {
        Insets insets = button.getInsets();
        Rectangle view = new Rectangle(
            insets.left,
            insets.top,
            button.getWidth() - insets.left - insets.right,
            button.getHeight() - insets.top - insets.bottom
        );
        Rectangle icon = new Rectangle();
        Rectangle text = new Rectangle();
        FontMetrics metrics = button.getFontMetrics(button.getFont());
        return SwingUtilities.layoutCompoundLabel(
            button,
            metrics,
            button.getText(),
            button.getIcon(),
            button.getVerticalAlignment(),
            button.getHorizontalAlignment(),
            button.getVerticalTextPosition(),
            button.getHorizontalTextPosition(),
            view,
            icon,
            text,
            button.getIconTextGap()
        );
    }

    private record FixedIcon(int width, int height) implements Icon {
        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }
}
