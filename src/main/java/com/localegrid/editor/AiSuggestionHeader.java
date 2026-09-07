package com.localegrid.editor;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBTextField;
import com.localegrid.llm.TranslationStyle;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import javax.swing.*;
import java.awt.*;

/** AI feedback follows the suggestion button without replacing the bottom table status. */
final class AiSuggestionHeader extends JPanel {
    private final JLabel aiStatus = new JLabel();
    private final JPanel styles = AiStyleButton.groupPanel();
    private final ButtonGroup styleGroup = new ButtonGroup();

    private final JBTextField sourceInput = new JBTextField();
    private Object sourceContext;

    JTextField sourceInput() { return sourceInput; }

    String sourceText() { return sourceInput.getText().strip(); }

    void setSourceContext(Object context) {
        if (sourceContext != context) {
            sourceContext = context;
            sourceInput.setText("");
        }
    }

    void setSourceAvailable(boolean visible, boolean enabled) {
        sourceInput.setVisible(visible);
        sourceInput.setEnabled(enabled);
        if (styles.getComponentCount() > 0) {
            ((JComponent) styles.getComponent(0)).setToolTipText(visible
                ? "설명한 상황에 맞는 화면 문구를 제안합니다."
                : TranslationStyle.PRESERVE.description());
        }
        revalidate();
        repaint();
    }

    TranslationStyle selectedStyle() {
        return TranslationStyle.valueOf(styleGroup.getSelection().getActionCommand());
    }

    void setStylesAvailable(boolean visible, boolean enabled) {
        styles.setVisible(visible);
        for (Component button : styles.getComponents()) button.setEnabled(enabled);
        revalidate();
    }

    AiSuggestionHeader(JLabel title, JButton suggestButton) {
        super(new BorderLayout());
        setOpaque(false);
        aiStatus.putClientProperty("html.disable", true);
        aiStatus.setFont(suggestButton.getFont());
        aiStatus.setVisible(false);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(10), 0)) {
            @Override public void doLayout() { /* Header lays out the row and its narrow variant. */ }
            @Override public boolean contains(int x, int y) {
                for (Component child : getComponents()) {
                    if (child.isVisible() && child.getBounds().contains(x, y)) return true;
                }
                return false;
            }
        };
        controls.setOpaque(false);
        controls.add(title);
        styles.setOpaque(false);
        for (TranslationStyle style : TranslationStyle.values()) {
            AiStyleButton radio = new AiStyleButton(style.label(), style == TranslationStyle.PRESERVE);
            radio.setOpaque(false);
            radio.setFont(suggestButton.getFont());
            radio.setToolTipText(style.description());
            radio.setActionCommand(style.name());
            styleGroup.add(radio);
            styles.add(radio);
        }
        for (int index = 0; index < styles.getComponentCount(); index++) {
            final int current = index;
            JComponent segment = (JComponent) styles.getComponent(index);
            for (int direction : new int[]{-1, 1}) {
                String action = direction < 0 ? "previousStyle" : "nextStyle";
                segment.getInputMap().put(KeyStroke.getKeyStroke(direction < 0 ? "LEFT" : "RIGHT"), action);
                segment.getActionMap().put(action, new AbstractAction() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                        AbstractButton next = (AbstractButton) styles.getComponent(
                            Math.floorMod(current + direction, styles.getComponentCount()));
                        if (next.isEnabled()) { next.doClick(); next.requestFocusInWindow(); }
                    }
                });
            }
        }
        suggestButton.addPropertyChangeListener("font", event -> {
            aiStatus.setFont(suggestButton.getFont());
            sourceInput.setFont(suggestButton.getFont());
            for (Component radio : styles.getComponents()) radio.setFont(suggestButton.getFont());
            revalidate();
        });
        styles.setVisible(false);
        controls.add(styles);
        controls.add(suggestButton);
        sourceInput.getEmptyText().setText("필요한 상황과 원하는 문구를 자유롭게 설명");
        sourceInput.setToolTipText("필요한 상황과 원하는 문구를 자유롭게 설명하세요. 개수를 지정하지 않으면 명확한 문구 1개를 우선 제안합니다.");
        sourceInput.getAccessibleContext().setAccessibleName("필요한 문구 설명");
        sourceInput.setFont(suggestButton.getFont());
        sourceInput.setBackground(new JBColor(Color.WHITE, new Color(43, 40, 49)));
        sourceInput.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new JBColor(new Color(213, 196, 234), new Color(89, 72, 111)), 1, true),
            JBUI.Borders.empty(0, 6)));
        sourceInput.setPreferredSize(JBUI.size(360, 24));
        sourceInput.setVisible(false);
        sourceInput.addActionListener(event -> suggestButton.doClick());
        controls.add(sourceInput);
        suggestButton.putClientProperty("localegrid.integrated", true);
        add(controls, BorderLayout.WEST);
        add(aiStatus, BorderLayout.CENTER);
    }

    private int controlsWidth() {
        Container controls = (Container) getComponent(0);
        int width = controls.getComponent(0).getPreferredSize().width + JBUI.scale(20);
        Component button = controls.getComponent(2);
        if (button.isVisible()) width += button.getPreferredSize().width + JBUI.scale(10);
        if (styles.isVisible()) width += styles.getPreferredSize().width + JBUI.scale(10);
        return width;
    }

    private boolean sourceBelow() {
        return sourceInput.isVisible() && getWidth() > 0
            && getWidth() < controlsWidth() + JBUI.scale(320);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension base = super.getPreferredSize();
        int rowHeight = Math.max(base.height, JBUI.scale(28));
        base.height = rowHeight;
        if (getWidth() > 0 && getWidth() < controlsWidth()) base.height += rowHeight + JBUI.scale(4);
        if (sourceBelow()) base.height += JBUI.scale(28);
        return base;
    }

    @Override
    public void doLayout() {
        Container controls = (Container) getComponent(0);
        Component title = controls.getComponent(0);
        Component button = controls.getComponent(2);
        int gap = JBUI.scale(10);
        int styleWidth = styles.isVisible() ? styles.getPreferredSize().width : 0;
        int buttonWidth = button.isVisible() ? button.getPreferredSize().width : 0;
        int rowHeight = Math.max(title.getPreferredSize().height,
            Math.max(styles.isVisible() ? styles.getPreferredSize().height : 0, button.getPreferredSize().height));
        boolean wrap = getWidth() < controlsWidth();
        boolean below = sourceBelow();
        int y = wrap ? rowHeight + JBUI.scale(4) : Math.max(0, (getHeight() - rowHeight - (below ? JBUI.scale(28) : 0)) / 2);
        int titleWidth = wrap ? Math.max(0, getWidth() - 2 * gap) : title.getPreferredSize().width;
        controls.setBounds(0, 0, getWidth(), getHeight());
        title.setBounds(gap, (wrap ? 0 : y) + (rowHeight - title.getPreferredSize().height) / 2,
            titleWidth, title.getPreferredSize().height);
        int x = wrap ? gap : titleWidth + 2 * gap;
        int groupLeft = x;
        styles.setBounds(x, y + (rowHeight - styles.getPreferredSize().height) / 2,
            styleWidth, styles.getPreferredSize().height);
        if (styles.isVisible()) x += styleWidth + gap;
        if (sourceInput.isVisible() && !below) {
            int inputWidth = Math.min(sourceInput.getPreferredSize().width, Math.max(0, getWidth() - x - buttonWidth - 2 * gap));
            sourceInput.setBounds(x, y + (rowHeight - JBUI.scale(24)) / 2, inputWidth, JBUI.scale(24));
            x += inputWidth + gap;
        }
        button.setBounds(x, y + (rowHeight - button.getPreferredSize().height) / 2,
            buttonWidth, button.getPreferredSize().height);
        x += buttonWidth + gap;
        if (below) {
            int available = Math.max(0, getWidth() - groupLeft - gap - JBUI.scale(3));
            int inputWidth = Math.min(available, Math.max(sourceInput.getPreferredSize().width,
                x - gap - groupLeft - JBUI.scale(6)));
            sourceInput.setBounds(groupLeft + JBUI.scale(3), y + rowHeight, inputWidth, JBUI.scale(24));
            x = Math.max(x, sourceInput.getX() + inputWidth + gap);
        }
        aiStatus.setBounds(x, wrap || below ? y : 0, Math.max(0, getWidth() - x), wrap || below ? rowHeight : getHeight());
        styles.doLayout();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (!styles.isVisible()) return;
        Container controls = (Container) getComponent(0);
        Component button = controls.getComponent(2);
        if (!button.isVisible()) return;
        int inset = JBUI.scale(3);
        int left = styles.getX() - inset;
        int top = styles.getY();
        int width = button.getX() + button.getWidth() - left + inset;
        int height = styles.getHeight();
        if (sourceBelow()) {
            height = sourceInput.getY() + sourceInput.getHeight() + JBUI.scale(4) - top;
            width = Math.max(width, sourceInput.getX() + sourceInput.getWidth() - left + inset);
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new JBColor(new Color(248, 245, 252), new Color(46, 43, 53)));
            g.fillRoundRect(left, top, width, height - 1, JBUI.scale(12), JBUI.scale(12));
            g.setColor(new JBColor(new Color(213, 196, 234), new Color(89, 72, 111)));
            g.drawRoundRect(left, top, width, height - 1, JBUI.scale(12), JBUI.scale(12));
            int divider = button.getX() - JBUI.scale(5);
            g.drawLine(divider, top + JBUI.scale(8), divider, top + styles.getHeight() - JBUI.scale(8));
        } finally { g.dispose(); }
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
