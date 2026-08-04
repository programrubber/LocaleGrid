package com.localegrid.editor;

import com.localegrid.model.LocaleGridRow;

import java.util.List;

final class SearchNavigationState {
    private LocaleGridRow current;

    void update(List<LocaleGridRow> matches, boolean resetToFirst) {
        if (matches.isEmpty()) {
            current = null;
            return;
        }
        if (resetToFirst || !matches.contains(current)) {
            current = matches.get(0);
        }
    }

    void syncToSelection(LocaleGridRow selectedRow, List<LocaleGridRow> matches) {
        if (selectedRow != null && matches.contains(selectedRow)) {
            current = selectedRow;
        }
    }

    LocaleGridRow move(List<LocaleGridRow> matches, int direction) {
        if (matches.isEmpty()) {
            current = null;
            return null;
        }
        int currentIndex = matches.indexOf(current);
        if (currentIndex < 0) {
            currentIndex = direction < 0 ? 0 : -1;
        }
        current = matches.get(Math.floorMod(currentIndex + direction, matches.size()));
        return current;
    }

    LocaleGridRow getCurrent() {
        return current;
    }

    int getCurrentIndex(List<LocaleGridRow> matches) {
        if (current == null) {
            return -1;
        }
        return matches.indexOf(current);
    }

    void clear() {
        current = null;
    }
}
