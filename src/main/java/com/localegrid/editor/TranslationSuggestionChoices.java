package com.localegrid.editor;

import com.intellij.util.ui.JBUI;
import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Candidate chips wrap at the available width; applying one clears only this locale's choices. */
final class TranslationSuggestionChoices extends JPanel {
    private final Consumer<Boolean> onVisibilityChanged;

    TranslationSuggestionChoices(String locale, Map<String, String> candidates,
                                 BiConsumer<String, String> onApply, Consumer<Boolean> onVisibilityChanged) {
        this.onVisibilityChanged = onVisibilityChanged;
        setOpaque(false);
        setLayout(new LayoutManager() {
            @Override public void addLayoutComponent(String name, Component component) { }
            @Override public void removeLayoutComponent(Component component) { }
            @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, 0); }
            @Override public Dimension preferredLayoutSize(Container parent) { return measure(false); }
            @Override public void layoutContainer(Container parent) { measure(true); }
        });
        for (Map.Entry<String, String> entry : candidates.entrySet()) {
            String id = entry.getKey();
            String candidate = entry.getValue();
            TranslationSuggestionChip chip = new TranslationSuggestionChip(locale, candidate, "[" + id + "안]", text -> {
                onApply.accept(id, text);
                removeAll();
                changed(onVisibilityChanged);
            }, () -> {
                for (Component child : getComponents()) {
                    if (id.equals(((JComponent) child).getClientProperty("candidateId"))) {
                        remove(child);
                        break;
                    }
                }
                changed(onVisibilityChanged);
            });
            chip.putClientProperty("candidateId", id);
            chip.getAccessibleContext().setAccessibleName(locale + " " + id + "안 번역 제안 적용: " + candidate);
            add(chip);
        }
    }

    void retainCandidate(String id) {
        for (Component child : getComponents()) {
            if (!id.equals(((JComponent) child).getClientProperty("candidateId"))) remove(child);
        }
        changed(onVisibilityChanged);
    }

    private void changed(Consumer<Boolean> onVisibilityChanged) {
        setVisible(getComponentCount() > 0);
        revalidate();
        repaint();
        onVisibilityChanged.accept(isVisible());
    }

    private Dimension measure(boolean place) {
        int width = getWidth() > 0 ? getWidth() : JBUI.scale(600);
        int gap = JBUI.scale(6);
        int x = 0, y = 0, rowHeight = 0, usedWidth = 0;
        for (Component chip : getComponents()) {
            Dimension size = chip.getPreferredSize();
            int chipWidth = Math.min(width, size.width);
            if (x > 0 && x + chipWidth > width) {
                x = 0;
                y += rowHeight + gap;
                rowHeight = 0;
            }
            if (place) chip.setBounds(x, y, chipWidth, size.height);
            usedWidth = Math.max(usedWidth, x + chipWidth);
            x += chipWidth + gap;
            rowHeight = Math.max(rowHeight, size.height);
        }
        return new Dimension(usedWidth, y + rowHeight);
    }
}
