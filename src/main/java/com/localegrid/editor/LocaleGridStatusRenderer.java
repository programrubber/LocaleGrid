package com.localegrid.editor;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class LocaleGridStatusRenderer extends JComponent implements TableCellRenderer {
    private static final int BADGE_WIDTH = 42;
    private static final int BADGE_HEIGHT = 20;
    private static final int BADGE_GAP = 4;
    private static final Font BADGE_FONT = new Font(Font.MONOSPACED, Font.BOLD, 11);
    private static final Color BADGE_TEXT = Color.WHITE;

    private JTable table;
    private List<String> codes = List.of();
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
        this.codes = statusCodes(value);
        this.selected = isSelected;
        this.focused = hasFocus;
        this.row = row;
        setToolTipText(tooltipText(value));
        return this;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            paintBackground(g);
            paintBadges(g);
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

    private void paintBadges(Graphics2D g) {
        if (codes.isEmpty()) {
            return;
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(BADGE_FONT);
        List<Rectangle> badges = badgeBounds(codes.size(), getWidth(), getHeight());
        FontMetrics metrics = g.getFontMetrics();

        for (int i = 0; i < badges.size(); i++) {
            String code = codes.get(i);
            Rectangle badge = badges.get(i);
            if (badge.width <= 0 || badge.height <= 0) {
                continue;
            }

            g.setColor(badgeColor(code));
            g.fillRoundRect(badge.x, badge.y, badge.width, badge.height, 6, 6);

            int textX = badge.x + (badge.width - metrics.stringWidth(code)) / 2;
            int textY = badge.y + ((badge.height - metrics.getHeight()) / 2) + metrics.getAscent();
            g.setColor(BADGE_TEXT);
            g.drawString(code, Math.max(badge.x + 3, textX), textY);
        }
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
            case "편집":
                return new Color(31, 132, 181);
            default:
                return new Color(96, 96, 96);
        }
    }

    static boolean containsBadgePoint(Object value, int x, int y, int cellWidth, int cellHeight) {
        List<String> codes = statusCodes(value);
        if (codes.isEmpty()) {
            return false;
        }
        for (Rectangle badge : badgeBounds(codes.size(), cellWidth, cellHeight)) {
            if (badge.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    static boolean containsBadgePoint(String code, int x, int y, int cellWidth, int cellHeight) {
        return containsBadgePoint((Object) code, x, y, cellWidth, cellHeight);
    }

    static String tooltipText(Object value) {
        List<String> codes = statusCodes(value);
        if (codes.isEmpty()) {
            return null;
        }
        StringBuilder tooltip = new StringBuilder();
        for (String code : codes) {
            if (tooltip.length() > 0) {
                tooltip.append(" / ");
            }
            tooltip.append(statusDescription(code));
        }
        return tooltip.toString();
    }

    static String tooltipText(String code) {
        return tooltipText((Object) code);
    }

    private static String statusDescription(String code) {
        switch (code) {
            case "에러":
                return "에러 - 중복 key 또는 dot path 충돌로 적용이 차단됩니다.";
            case "추가":
                return "추가 - 적용 시 새로 추가될 Row입니다.";
            case "경고":
                return "경고 - 빈 값 또는 누락된 번역이 있어 확인이 필요합니다.";
            case "삭제":
                return "삭제 - 적용 시 제거될 Row입니다.";
            case "편집":
                return "편집 - 적용하지 않은 변경 사항이 있습니다.";
            default:
                return code;
        }
    }

    private static List<String> statusCodes(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                addCode(result, item);
            }
            return result;
        }
        addCode(result, value);
        return result;
    }

    private static void addCode(List<String> result, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            result.add(text);
        }
    }

    private static List<Rectangle> badgeBounds(int count, int cellWidth, int cellHeight) {
        List<Rectangle> badges = new ArrayList<>();
        if (count <= 0) {
            return badges;
        }

        int totalGap = BADGE_GAP * (count - 1);
        int availableWidth = Math.max(0, cellWidth - 8 - totalGap);
        int badgeWidth = Math.min(BADGE_WIDTH, count == 0 ? 0 : availableWidth / count);
        int totalWidth = badgeWidth * count + totalGap;
        int x = Math.max(4, (cellWidth - totalWidth) / 2);
        int y = Math.max(3, (cellHeight - BADGE_HEIGHT) / 2);

        for (int i = 0; i < count; i++) {
            badges.add(new Rectangle(x + i * (badgeWidth + BADGE_GAP), y, badgeWidth, BADGE_HEIGHT));
        }
        return badges;
    }
}
