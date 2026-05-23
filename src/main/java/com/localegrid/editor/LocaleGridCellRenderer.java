package com.localegrid.editor;

import com.localegrid.model.LocaleGridRow;
import com.localegrid.model.LocaleValue;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

class LocaleGridCellRenderer extends JPanel implements TableCellRenderer {
    private static final Color ERROR_BG = new Color(255, 226, 226);
    private static final Color WARNING_BG = new Color(255, 247, 214);
    private static final Color DELETED_BG = new Color(240, 240, 240);
    private static final Color COMMENT_BG = new Color(235, 242, 255);
    private static final Color READONLY_BG = new Color(245, 245, 245);
    private static final Color MISSING_BG = new Color(255, 250, 230);

    private final JLabel badge = new JLabel("", SwingConstants.CENTER);
    private final JTextArea text = new JTextArea();

    LocaleGridCellRenderer() {
        super(new BorderLayout(6, 0));
        badge.setOpaque(true);
        badge.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        badge.setPreferredSize(new Dimension(42, 20));
        badge.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));

        text.setOpaque(false);
        text.setEditable(false);
        text.setLineWrap(false);
        text.setWrapStyleWord(false);
        text.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        add(badge, BorderLayout.WEST);
        add(text, BorderLayout.CENTER);
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
        LocaleGridTableModel model = (LocaleGridTableModel) table.getModel();
        LocaleGridRow gridRow = model.getRow(row);
        boolean keyColumn = model.isKeyColumn(column);

        setBorder(hasFocus ? UIManager.getBorder("Table.focusCellHighlightBorder") : BorderFactory.createEmptyBorder());
        Color background = resolveBackground(table, model, gridRow, row, column, isSelected);
        Color foreground = isSelected ? table.getSelectionForeground() : table.getForeground();
        setBackground(background);
        text.setBackground(background);
        text.setForeground(foreground);

        if (keyColumn) {
            configureBadge(model.getStatusCode(gridRow), isSelected);
            text.setText(gridRow.getKey());
        } else {
            badge.setVisible(false);
            text.setText(value == null ? "" : String.valueOf(value));
        }
        return this;
    }

    private Color resolveBackground(
        JTable table,
        LocaleGridTableModel model,
        LocaleGridRow row,
        int viewRow,
        int column,
        boolean isSelected
    ) {
        if (isSelected) {
            return table.getSelectionBackground();
        }
        if (row.isDeleted()) {
            return DELETED_BG;
        }
        if (model.hasError(row)) {
            return ERROR_BG;
        }
        if (row.isComment()) {
            return COMMENT_BG;
        }
        if (model.hasWarning(row)) {
            return WARNING_BG;
        }
        if (!model.isKeyColumn(column) && !model.isBundleColumn(column)) {
            String locale = model.getLocaleForColumn(column);
            LocaleValue cellValue = locale == null ? null : row.getValue(locale);
            if (cellValue != null && (!cellValue.isPresent() || cellValue.getDisplayText().isEmpty())) {
                return MISSING_BG;
            }
            if (model.isReadonlyCell(row, column)) {
                return READONLY_BG;
            }
        }
        return viewRow % 2 == 0 ? Color.WHITE : new Color(250, 251, 252);
    }

    private void configureBadge(String code, boolean isSelected) {
        if (code == null || code.isEmpty()) {
            badge.setVisible(false);
            return;
        }
        badge.setVisible(true);
        badge.setText(code);
        badge.setForeground(isSelected ? Color.WHITE : Color.DARK_GRAY);
        switch (code) {
            case "ERR!":
                badge.setBackground(new Color(255, 184, 184));
                break;
            case "WARN":
                badge.setBackground(new Color(255, 228, 140));
                break;
            case "DEL!":
                badge.setBackground(new Color(210, 210, 210));
                break;
            case "READ":
                badge.setBackground(new Color(194, 215, 255));
                break;
            case "MOD*":
                badge.setBackground(new Color(205, 236, 255));
                break;
            default:
                badge.setBackground(new Color(230, 230, 230));
        }
    }
}
