package com.localegrid.settings;

import com.intellij.util.messages.Topic;

@FunctionalInterface
public interface LocaleGridSettingsListener {
    Topic<LocaleGridSettingsListener> TOPIC = Topic.create(
        "LocaleGrid settings changed",
        LocaleGridSettingsListener.class
    );

    void settingsChanged(boolean structuralSettingsChanged);
}
