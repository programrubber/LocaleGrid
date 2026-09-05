package com.localegrid.editor;

import javax.swing.*;
import java.awt.*;

/** Width-tracking scrollable rows, measured at their actual width before placement. */
final class TranslationDetailFields extends JPanel implements Scrollable {
    TranslationDetailFields() {
        setLayout(new LayoutManager() {
            @Override public void addLayoutComponent(String name, Component component) { }
            @Override public void removeLayoutComponent(Component component) { }
            @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, 0); }
            @Override public Dimension preferredLayoutSize(Container parent) { return measure(false); }
            @Override public void layoutContainer(Container parent) { measure(true); }
        });
    }

    private Dimension measure(boolean place) {
        Insets insets = getInsets();
        int width = Math.max(1, (getWidth() > 0 ? getWidth() : 600) - insets.left - insets.right);
        int y = insets.top;
        for (Component row : getComponents()) {
            if (!row.isVisible()) continue;
            row.setSize(width, row.getHeight());
            int height = row.getPreferredSize().height;
            if (place) row.setBounds(insets.left, y, width, height);
            y += height;
        }
        return new Dimension(width + insets.left + insets.right, y + insets.bottom);
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 24; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(24, visible.height - 24); }
}
