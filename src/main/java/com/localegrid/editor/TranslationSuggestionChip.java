package com.localegrid.editor;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/** Clickable translation preview with a separate dismiss action. */
public class TranslationSuggestionChip extends JPanel {
    private static final Color BACKGROUND = new JBColor(new Color(248, 246, 251), new Color(48, 45, 54));
    private static final Color BORDER = new JBColor(new Color(222, 214, 234), new Color(76, 67, 89));
    private static final Color TEXT = new JBColor(new Color(62, 53, 74), new Color(226, 220, 235));
    private final String locale;
    private final String suggestedText;
    private final JLabel preview;

    public TranslationSuggestionChip(String locale, String suggestedText,
                                     Consumer<String> onApply, Runnable onDismiss) {
        super(new BorderLayout(JBUI.scale(6), 0));
        this.locale = locale;
        this.suggestedText = suggestedText;
        setOpaque(false);
        setBorder(JBUI.Borders.empty(4, 10));
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        String tooltip = "<html><div width='420'>" + suggestedText.replace("&", "&amp;")
            .replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</div></html>";
        setToolTipText(tooltip);
        getAccessibleContext().setAccessibleName(locale + " 번역 제안 적용");
        Runnable applySuggestion = () -> { if (onApply != null) onApply.accept(suggestedText); };
        MouseAdapter applyOnClick = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 1) applySuggestion.run();
            }
        };
        addMouseListener(applyOnClick);
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "applySuggestion");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "applySuggestion");
        getActionMap().put("applySuggestion", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { applySuggestion.run(); }
        });
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) { repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent event) { repaint(); }
        });

        preview = new JLabel(suggestedText.replace('\n', ' ').replace('\r', ' '));
        preview.putClientProperty("html.disable", true);
        preview.setOpaque(false);
        preview.setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 11f));
        preview.setForeground(TEXT);
        preview.setBorder(JBUI.Borders.empty());
        preview.setFocusable(false);
        preview.setCursor(getCursor());
        preview.setToolTipText(tooltip);
        preview.addMouseListener(applyOnClick);
        preview.getAccessibleContext().setAccessibleName(locale + " AI 번역 제안");

        JLabel icon = new JLabel(new AiTranslationButton.SparkleIcon() {
            @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics colored = graphics.create();
                try {
                    colored.setColor(AiTranslationButton.ACCENT);
                    super.paintIcon(component, colored, x, y);
                } finally { colored.dispose(); }
            }
        });
        icon.setToolTipText(tooltip);
        icon.addMouseListener(applyOnClick);
        JButton dismiss = new AiTranslationButton("", new AiTranslationButton.CloseIcon(), false) {
            @Override public Dimension getPreferredSize() {
                return new Dimension(JBUI.scale(20), JBUI.scale(20));
            }
        };
        dismiss.setBorder(JBUI.Borders.empty(2));
        dismiss.setToolTipText("제안 닫기");
        dismiss.getAccessibleContext().setAccessibleName(locale + " 번역 제안 닫기");
        dismiss.addActionListener(event -> { if (onDismiss != null) onDismiss.run(); });
        add(preview, BorderLayout.CENTER);
        add(icon, BorderLayout.WEST);
        add(dismiss, BorderLayout.EAST);
    }

    public String getTargetLocale() { return locale; }
    public String getSuggestedText() { return suggestedText; }

    @Override
    public Dimension getPreferredSize() {
        Dimension natural = super.getPreferredSize();
        int width = natural.width;
        if (getParent() != null && getParent().getWidth() > 0) width = Math.min(width, getParent().getWidth());
        return new Dimension(width, Math.max(JBUI.scale(28), natural.height));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = JBUI.scale(8);
            g.setColor(BACKGROUND);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g.setColor(hasFocus() ? AiTranslationButton.ACCENT : BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        } finally { g.dispose(); }
        super.paintComponent(graphics);
    }
}
