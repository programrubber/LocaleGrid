package com.localegrid.editor;

import javax.swing.Timer;

final class SwingDebouncer {
    private final Timer timer;
    private final Runnable action;
    private boolean pending;

    SwingDebouncer(int delayMillis, Runnable action) {
        this.action = action;
        timer = new Timer(delayMillis, event -> runPendingAction());
        timer.setRepeats(false);
    }

    void restart() {
        pending = true;
        timer.restart();
    }

    void flush() {
        if (!pending) {
            return;
        }
        timer.stop();
        runPendingAction();
    }

    void cancel() {
        timer.stop();
        pending = false;
    }

    boolean isPending() {
        return pending;
    }

    private void runPendingAction() {
        if (!pending) {
            return;
        }
        pending = false;
        action.run();
    }
}
