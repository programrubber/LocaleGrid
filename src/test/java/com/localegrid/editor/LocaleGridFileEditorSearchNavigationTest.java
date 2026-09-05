package com.localegrid.editor;

import com.localegrid.model.LocaleGridRow;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LocaleGridFileEditorSearchNavigationTest {

    @Test
    void searchFieldKeyBindingsTriggerNavigationInBothDirections() {
        JTextField searchField = new JTextField();
        List<Integer> directions = new ArrayList<>();
        LocaleGridFileEditor.installSearchKeyboardNavigation(searchField, directions::add);

        InputMap inputMap = searchField.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = searchField.getActionMap();

        // 1. VK_UP -> previous match (-1)
        Object upKey = inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
        assertNotNull(upKey, "VK_UP must be mapped in inputMap");
        Action upAction = actionMap.get(upKey);
        assertNotNull(upAction, "Action for VK_UP must exist");
        upAction.actionPerformed(new ActionEvent(searchField, ActionEvent.ACTION_PERFORMED, null));
        assertEquals(List.of(-1), directions);

        // 2. VK_DOWN -> next match (1)
        Object downKey = inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
        assertNotNull(downKey, "VK_DOWN must be mapped in inputMap");
        Action downAction = actionMap.get(downKey);
        assertNotNull(downAction, "Action for VK_DOWN must exist");
        downAction.actionPerformed(new ActionEvent(searchField, ActionEvent.ACTION_PERFORMED, null));
        assertEquals(List.of(-1, 1), directions);

        // 3. VK_ENTER -> next match (1)
        Object enterKey = inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
        assertNotNull(enterKey, "VK_ENTER must be mapped in inputMap");
        Action enterAction = actionMap.get(enterKey);
        assertNotNull(enterAction, "Action for VK_ENTER must exist");
        enterAction.actionPerformed(new ActionEvent(searchField, ActionEvent.ACTION_PERFORMED, null));
        assertEquals(List.of(-1, 1, 1), directions);

        // 4. Shift + VK_ENTER -> previous match (-1)
        Object shiftEnterKey = inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK));
        assertNotNull(shiftEnterKey, "Shift + VK_ENTER must be mapped in inputMap");
        Action shiftEnterAction = actionMap.get(shiftEnterKey);
        assertNotNull(shiftEnterAction, "Action for Shift + VK_ENTER must exist");
        shiftEnterAction.actionPerformed(new ActionEvent(searchField, ActionEvent.ACTION_PERFORMED, null));
        assertEquals(List.of(-1, 1, 1, -1), directions);
    }

    @Test
    void threeMatchesKeyboardNavigationScenario() {
        LocaleGridRow match1 = new LocaleGridRow("header.title", false);
        LocaleGridRow match2 = new LocaleGridRow("button.submit", false);
        LocaleGridRow match3 = new LocaleGridRow("footer.copyright", false);
        List<LocaleGridRow> matches = List.of(match1, match2, match3);

        SearchNavigationState state = new SearchNavigationState();
        state.update(matches, true);
        assertEquals(match1, state.getCurrent(), "Initially the first match should be selected");

        JTextField searchField = new JTextField();
        LocaleGridFileEditor.installSearchKeyboardNavigation(searchField, direction -> state.move(matches, direction));

        ActionMap actionMap = searchField.getActionMap();
        InputMap inputMap = searchField.getInputMap(JComponent.WHEN_FOCUSED);

        Action nextAction = actionMap.get(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)));
        Action prevAction = actionMap.get(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)));

        // Down arrow -> moves to match 2
        nextAction.actionPerformed(new ActionEvent(searchField, ActionEvent.ACTION_PERFORMED, null));
        assertEquals(match2, state.getCurrent());

        // Down arrow -> moves to match 3
        nextAction.actionPerformed(new ActionEvent(searchField, ActionEvent.ACTION_PERFORMED, null));
        assertEquals(match3, state.getCurrent());

        // Down arrow -> wraps around to match 1
        nextAction.actionPerformed(new ActionEvent(searchField, ActionEvent.ACTION_PERFORMED, null));
        assertEquals(match1, state.getCurrent());

        // Up arrow -> wraps backwards to match 3
        prevAction.actionPerformed(new ActionEvent(searchField, ActionEvent.ACTION_PERFORMED, null));
        assertEquals(match3, state.getCurrent());

        // Up arrow -> moves to match 2
        prevAction.actionPerformed(new ActionEvent(searchField, ActionEvent.ACTION_PERFORMED, null));
        assertEquals(match2, state.getCurrent());
    }
}
