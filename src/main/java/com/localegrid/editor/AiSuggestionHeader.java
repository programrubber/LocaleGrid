package com.localegrid.editor;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import javax.swing.*;
import java.awt.*;

/** AI feedback follows the suggestion button without replacing the bottom table status. */
final class AiSuggestionHeader extends JPanel {
    private final JLabel aiStatus = new JLabel();

    AiSuggestionHeader(JLabel title, JButton suggestButton) {
        super(new BorderLayout());
        setOpaque(false);
        aiStatus.putClientProperty("html.disable", true);
        aiStatus.setFont(suggestButton.getFont());
        aiStatus.setVisible(false);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(10), 0));
        controls.setOpaque(false);
        controls.add(title);
        controls.add(suggestButton);
        add(controls, BorderLayout.WEST);
        add(aiStatus, BorderLayout.CENTER);
    }

    void showAiStatus(String message, boolean error) {
        aiStatus.setText(message);
        aiStatus.setToolTipText(message);
        aiStatus.setForeground(error ? JBColor.RED : UIUtil.getContextHelpForeground());
        aiStatus.setVisible(true);
        revalidate();
        repaint();
    }

    void clearAiStatus() {
        aiStatus.setText("");
        aiStatus.setToolTipText(null);
        aiStatus.setVisible(false);
        revalidate();
        repaint();
    }
}
