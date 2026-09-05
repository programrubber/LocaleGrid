package com.localegrid.editor;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import javax.swing.*;
import java.awt.*;

/** Compact AI controls with explicit hover, disabled and keyboard-focus states. */
class AiTranslationButton extends JButton {
    static final Color ACCENT = new JBColor(new Color(105, 69, 153), new Color(206, 185, 239));
    private static final Color FILL = new JBColor(new Color(245, 241, 250), new Color(57, 51, 66));
    private static final Color HOVER = new JBColor(new Color(236, 228, 247), new Color(70, 59, 84));
    private static final Color PRESSED = new JBColor(new Color(224, 211, 240), new Color(81, 65, 99));
    private static final Color BORDER = new JBColor(new Color(205, 188, 226), new Color(102, 83, 128));
    private static final Color MUTED = new JBColor(new Color(148, 143, 154), new Color(133, 127, 144));
    private static final Color DISABLED_FILL = new JBColor(new Color(244, 243, 246), new Color(55, 54, 59));
    private static final Color DISABLED_BORDER = new JBColor(new Color(221, 217, 227), new Color(73, 70, 79));
    private final boolean outlined;

    AiTranslationButton(String text, Icon icon, boolean outlined) {
        super(text, icon);
        this.outlined = outlined;
        setFont(getFont().deriveFont(Font.PLAIN, 12f));
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setIconTextGap(JBUI.scale(6));
        setBorder(JBUI.Borders.empty(5, 10));
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        int iconWidth = getIcon() == null ? 0 : getIcon().getIconWidth();
        int textWidth = getText().isEmpty() ? 0 : metrics.stringWidth(getText());
        int gap = iconWidth > 0 && textWidth > 0 ? getIconTextGap() : 0;
        Insets padding = getInsets();
        return new Dimension(Math.max(JBUI.scale(28), iconWidth + gap + textWidth + padding.left + padding.right),
            Math.max(JBUI.scale(28), Math.max(metrics.getHeight(), getIcon() == null ? 0 : getIcon().getIconHeight())
                + padding.top + padding.bottom));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            ButtonModel state = getModel();
            boolean pressed = isEnabled() && state.isPressed() && state.isArmed();
            boolean hover = isEnabled() && state.isRollover();
            int arc = JBUI.scale(8);
            if (outlined || hover || pressed) {
                g.setColor(!isEnabled() ? DISABLED_FILL : pressed ? PRESSED : hover ? HOVER : FILL);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            }
            if (outlined || hasFocus()) {
                g.setColor(!isEnabled() ? DISABLED_BORDER : hasFocus() ? ACCENT : BORDER);
                g.setStroke(new BasicStroke(hasFocus() ? JBUI.scale(2f) : JBUI.scale(1f)));
                g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
            }
            g.setColor(isEnabled() && outlined ? ACCENT : MUTED);
            g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            int iconWidth = getIcon() == null ? 0 : getIcon().getIconWidth();
            int gap = iconWidth > 0 && !getText().isEmpty() ? getIconTextGap() : 0;
            int x = (getWidth() - iconWidth - gap - metrics.stringWidth(getText())) / 2;
            if (getIcon() != null) {
                getIcon().paintIcon(this, g, x, (getHeight() - getIcon().getIconHeight()) / 2);
            }
            g.drawString(getText(), x + iconWidth + gap,
                (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
        } finally {
            g.dispose();
        }
    }

    static class SparkleIcon implements Icon {
        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(x, y);
                g.scale(getIconWidth() / 16.0, getIconHeight() / 16.0);
                g.fillPolygon(new int[]{6, 8, 12, 8, 6, 4, 0, 4},
                    new int[]{3, 7, 9, 11, 15, 11, 9, 7}, 8);
                g.fillPolygon(new int[]{12, 13, 16, 13, 12, 11, 8, 11},
                    new int[]{0, 3, 4, 5, 8, 5, 4, 3}, 8);
            } finally { g.dispose(); }
        }
        @Override public int getIconWidth() { return JBUI.scale(14); }
        @Override public int getIconHeight() { return JBUI.scale(14); }
    }

    static final class CloseIcon implements Icon {
        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setStroke(new BasicStroke(JBUI.scale(1.4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int inset = JBUI.scale(3);
                g.drawLine(x + inset, y + inset, x + getIconWidth() - inset, y + getIconHeight() - inset);
                g.drawLine(x + getIconWidth() - inset, y + inset, x + inset, y + getIconHeight() - inset);
            } finally { g.dispose(); }
        }
        @Override public int getIconWidth() { return JBUI.scale(12); }
        @Override public int getIconHeight() { return JBUI.scale(12); }
    }
}
