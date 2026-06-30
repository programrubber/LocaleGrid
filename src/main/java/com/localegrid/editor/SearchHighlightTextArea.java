package com.localegrid.editor;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

class SearchHighlightTextArea extends JTextArea {
    private static final Color SEARCH_HIGHLIGHT_FG = Color.WHITE;

    private List<LocaleGridCellRenderer.HighlightRange> searchHighlightRanges = List.of();

    void setSearchHighlightRanges(List<LocaleGridCellRenderer.HighlightRange> searchHighlightRanges) {
        this.searchHighlightRanges = searchHighlightRanges;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (searchHighlightRanges.isEmpty()) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setFont(getFont().deriveFont(Font.BOLD));
            g2.setColor(SEARCH_HIGHLIGHT_FG);
            FontMetrics metrics = g2.getFontMetrics();
            String content = getText();
            for (LocaleGridCellRenderer.HighlightRange range : searchHighlightRanges) {
                Rectangle start = modelToView(range.start());
                if (start == null) {
                    continue;
                }
                String highlightedText = content.substring(range.start(), range.end());
                int baseline = start.y + metrics.getAscent();
                g2.drawString(highlightedText, start.x, baseline);
            }
        } catch (BadLocationException ignored) {
            // The renderer owns the text and ranges, so this should not happen.
        } finally {
            g2.dispose();
        }
    }
}
