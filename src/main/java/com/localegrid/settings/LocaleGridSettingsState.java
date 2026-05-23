package com.localegrid.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@State(name = "LocaleGridSettings", storages = @Storage("localeGrid.xml"))
public class LocaleGridSettingsState implements PersistentStateComponent<LocaleGridSettingsState> {
    public String localesRoot = "locales";
    public String manualLocales = "";
    public String commentKeys = "__comment__,__commant__";
    public int jsonIndent = 2;

    public static LocaleGridSettingsState getInstance(Project project) {
        LocaleGridSettingsState state = project.getService(LocaleGridSettingsState.class);
        return state == null ? new LocaleGridSettingsState() : state;
    }

    @Override
    public @Nullable LocaleGridSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull LocaleGridSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public List<String> getManualLocaleList() {
        return splitCsv(manualLocales);
    }

    public List<String> getCommentKeyList() {
        List<String> keys = splitCsv(commentKeys);
        return keys.isEmpty() ? Arrays.asList("__comment__", "__commant__") : keys;
    }

    private static List<String> splitCsv(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            return result;
        }
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
