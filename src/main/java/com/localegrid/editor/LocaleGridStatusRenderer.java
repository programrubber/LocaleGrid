package com.localegrid.editor;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

class LocaleGridStatusRenderer extends JComponent implements TableCellRenderer {
    private static final int BADGE_WIDTH = 48;
    private static final int BADGE_HEIGHT = 20;
    private static final Font BADGE_FONT = new Font(Font.MONOSPACED, Font.BOLD, 11);
    private static final Color BADGE_TEXT = Color.WHITE;

    private JTable table;
    private String code = "";
    private boolean selected;
    private boolean focused;
    private int row;

    LocaleGridStatusRenderer() {
        setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable table,
        Object value,
        boolean isSelected,
        boolean hasFocus,
        int row,
        int column
    ) {
        this.table = table;
        this.code = value == null ? "" : String.valueOf(value);
        this.selected = isSelected;
        this.focused = hasFocus;
        this.row = row;
        setToolTipText(tooltipText(code));
        return this;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            paintBackground(g);
            paintBadge(g);
            paintFocus(g);
        } finally {
            g.dispose();
        }
    }

    private void paintBackground(Graphics2D g) {
        Color background = selected ? table.getSelectionBackground() : rowBackground(row);
        g.setColor(background);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private void paintBadge(Graphics2D g) {
        if (code.isEmpty()) {
            return;
        }

        Rectangle badge = badgeBounds(getWidth(), getHeight());
        if (badge.width <= 0 || badge.height <= 0) {
            return;
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(badgeColor(code));
        g.fillRoundRect(badge.x, badge.y, badge.width, badge.height, 6, 6);

        g.setFont(BADGE_FONT);
        FontMetrics metrics = g.getFontMetrics();
        int textX = badge.x + (badge.width - metrics.stringWidth(code)) / 2;
        int textY = badge.y + ((badge.height - metrics.getHeight()) / 2) + metrics.getAscent();
        g.setColor(BADGE_TEXT);
        g.drawString(code, textX, textY);
    }

    private void paintFocus(Graphics2D g) {
        if (!focused) {
            return;
        }
        Border border = UIManager.getBorder("Table.focusCellHighlightBorder");
        if (border != null) {
            border.paintBorder(this, g, 0, 0, getWidth(), getHeight());
        }
    }

    private static Color rowBackground(int row) {
        return row % 2 == 0 ? new Color(60, 64, 65) : new Color(56, 60, 61);
    }

    static Color badgeColor(String code) {
        switch (code) {
            case "에러":
                return new Color(197, 61, 61);
            case "추가":
                return new Color(50, 130, 89);
            case "경고":
                return new Color(184, 119, 0);
            case "삭제":
                return new Color(104, 104, 104);
            case "읽기":
                return new Color(76, 131, 214);
            case "편집":
                return new Color(31, 132, 181);
            default:
                return new Color(96, 96, 96);
        }
    }

    static boolean containsBadgePoint(String code, int x, int y, int cellWidth, int cellHeight) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        return badgeBounds(cellWidth, cellHeight).contains(x, y);
    }

    static String tooltipText(String code) {
        switch (code) {
            case "에러":
                return "에러 - 중복 key 또는 dot path 충돌로 적용이 차단됩니다.";
            case "추가":
                return "추가 - 적용 시 새로 추가될 Row입니다.";
            case "경고":
                return "경고 - 빈 값 또는 누락된 번역이 있어 확인이 필요합니다.";
            case "삭제":
                return "삭제 - 적용 시 제거될 Row입니다.";
            case "읽기":
                return "읽기 - 주석 또는 편집할 수 없는 Row입니다.";
            case "편집":
                return "편집 - 적용되지 않은 변경 사항이 있습니다.";
            default:
                return null;
        }
    }

    private static Rectangle badgeBounds(int cellWidth, int cellHeight) {
        int width = Math.min(BADGE_WIDTH, Math.max(0, cellWidth - 8));
        int height = Math.min(BADGE_HEIGHT, Math.max(0, cellHeight - 6));
        int x = Math.max(4, (cellWidth - width) / 2);
        int y = Math.max(3, (cellHeight - height) / 2);
        return new Rectangle(x, y, width, height);
    }
}
