package com.localegrid.editor;

import com.localegrid.model.LocaleGridRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchNavigationStateTest {
    @Test
    void startsAtFirstMatchAndWrapsInBothDirections() {
        LocaleGridRow first = new LocaleGridRow("first", false);
        LocaleGridRow second = new LocaleGridRow("second", false);
        List<LocaleGridRow> matches = List.of(first, second);
        SearchNavigationState state = new SearchNavigationState();

        state.update(matches, true);

        assertEquals(first, state.getCurrent());
        assertEquals(second, state.move(matches, -1));
        assertEquals(first, state.move(matches, 1));
        assertEquals(second, state.move(matches, 1));
        assertEquals(first, state.move(matches, 1));
    }

    @Test
    void keepsCurrentMatchWhenResultsAreRecalculated() {
        LocaleGridRow first = new LocaleGridRow("first", false);
        LocaleGridRow second = new LocaleGridRow("second", false);
        SearchNavigationState state = new SearchNavigationState();
        state.update(List.of(first, second), true);
        state.move(List.of(first, second), 1);

        state.update(List.of(first, second), false);

        assertEquals(second, state.getCurrent());
        assertEquals(1, state.getCurrentIndex(List.of(first, second)));
    }

    @Test
    void clearsCurrentMatchWhenNoResultsRemain() {
        SearchNavigationState state = new SearchNavigationState();
        state.update(List.of(new LocaleGridRow("first", false)), true);

        state.update(List.of(), false);

        assertNull(state.getCurrent());
        assertEquals(-1, state.getCurrentIndex(List.of()));
    }
}
