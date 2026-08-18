package com.localegrid.editor;

import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
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
            assertFullLabel("추가", 66, 8);
            assertFullLabel("편집", 66, 8);
            assertFullLabel("삭제", 66, 8);
            assertFullLabel("삭제 취소", 104, 8);
            assertFullLabel("예외키", 84, 4);
            assertFullLabel("설정", 64, 4);
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
