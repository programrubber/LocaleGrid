package com.localegrid.editor;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import javax.swing.*;
import java.awt.*;

/** A non-shrinking detail row with a compact suggestion above the editor. */
final class TranslationDetailRow extends JPanel {
    private final JPanel suggestions;
    private final JScrollPane input;
    private final JLabel label;

    TranslationDetailRow(JLabel label, JTextArea editor, JPanel suggestions) {
        super(new BorderLayout(JBUI.scale(12), 0));
        this.label = label;
        this.suggestions = suggestions;
        input = new JBScrollPane(editor);
        setOpaque(false);
        setBorder(JBUI.Borders.empty(6, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setBorder(JBUI.Borders.emptyTop(8));
        editor.setMargin(JBUI.insets(7, 9));
        JPanel field = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        field.setOpaque(false);
        field.add(suggestions, BorderLayout.NORTH);
        field.add(input, BorderLayout.CENTER);
        add(label, BorderLayout.WEST);
        add(field, BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredSize() {
        int width = getWidth() > 0 ? getWidth() : JBUI.scale(600);
        int suggestionHeight = 0;
        if (suggestions.isVisible() && suggestions.getComponentCount() > 0) {
            Component card = suggestions.getComponent(0);
            suggestionHeight = card.getPreferredSize().height + JBUI.scale(6);
        }
        return new Dimension(width, getInsets().top + getInsets().bottom
            + input.getPreferredSize().height + suggestionHeight);
    }

    @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

}
