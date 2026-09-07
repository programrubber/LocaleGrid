package com.localegrid.editor;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import javax.swing.*;
import java.awt.*;

/** A style segment using the same violet palette as the AI suggestion action. */
final class AiStyleButton extends JToggleButton {
    private static final Color SELECTED_TOP = new JBColor(new Color(235, 223, 253), new Color(87, 65, 117));
    private static final Color SELECTED_BOTTOM = new JBColor(new Color(222, 205, 247), new Color(65, 50, 86));
    private static final Color HOVER = new JBColor(new Color(237, 231, 246), new Color(65, 59, 74));

    AiStyleButton(String text, boolean selected) {
        super(text, selected);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setBorder(JBUI.Borders.empty(2, 9));
        getAccessibleContext().setAccessibleName("번역 스타일: " + text);
    }

    @Override public Dimension getPreferredSize() {
        return new Dimension(getFontMetrics(getFont()).stringWidth(getText()) + JBUI.scale(20), JBUI.scale(24));
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(0.45f));
            int arc = JBUI.scale(10);
            if (isSelected()) {
                g.setPaint(new GradientPaint(0, 0, SELECTED_TOP, 0, getHeight(), SELECTED_BOTTOM));
                g.fillRoundRect(1, 1, getWidth()-2, getHeight()-2, arc, arc);
            } else if (isEnabled() && getModel().isRollover()) {
                g.setColor(HOVER);
                g.fillRoundRect(1, 1, getWidth()-2, getHeight()-2, arc, arc);
            }
            if (hasFocus()) {
                g.setColor(AiTranslationButton.ACCENT);
                g.setStroke(new BasicStroke(JBUI.scale(2f)));
                g.drawRoundRect(2, 2, getWidth()-5, getHeight()-5, arc, arc);
            }
            g.setFont(getFont());
            g.setColor(isSelected() ? AiTranslationButton.ACCENT : UIUtil.getContextHelpForeground());
            FontMetrics metrics = g.getFontMetrics();
            int textWidth = metrics.stringWidth(getText());
            int x = (getWidth() - textWidth) / 2;
            g.drawString(getText(), x, (getHeight()-metrics.getHeight())/2 + metrics.getAscent());
        } finally { g.dispose(); }
    }

    static JPanel groupPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(3), JBUI.scale(2)));
        panel.setOpaque(false);
        panel.getAccessibleContext().setAccessibleName("AI 번역 스타일");
        return panel;
    }
}
