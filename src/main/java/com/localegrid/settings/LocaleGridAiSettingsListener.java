package com.localegrid.settings;

import com.intellij.util.messages.Topic;

@FunctionalInterface
public interface LocaleGridAiSettingsListener {
    Topic<LocaleGridAiSettingsListener> TOPIC = Topic.create(
        "LocaleGrid AI settings changed", LocaleGridAiSettingsListener.class
    );

    void settingsChanged();
}
