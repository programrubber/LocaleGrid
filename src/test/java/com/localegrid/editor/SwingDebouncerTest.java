package com.localegrid.editor;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingDebouncerTest {
    @Test
    void flushRunsPendingActionExactlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        SwingDebouncer debouncer = new SwingDebouncer(10_000, calls::incrementAndGet);

        debouncer.restart();
        debouncer.restart();
        assertTrue(debouncer.isPending());

        debouncer.flush();
        debouncer.flush();

        assertEquals(1, calls.get());
        assertFalse(debouncer.isPending());
    }

    @Test
    void cancelDropsPendingAction() {
        AtomicInteger calls = new AtomicInteger();
        SwingDebouncer debouncer = new SwingDebouncer(10_000, calls::incrementAndGet);

        debouncer.restart();
        debouncer.cancel();

        assertEquals(0, calls.get());
        assertFalse(debouncer.isPending());
    }
}
