package com.localegrid.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@State(name = "LocaleGridSettings", storages = @Storage("localeGrid.xml"))
public class LocaleGridSettingsState implements PersistentStateComponent<LocaleGridSettingsState> {
    public String localesRoot = "locales";
    public String manualLocales = "";
    public String exceptionKeys = "__section__";
    public int jsonIndent = 2;
    public boolean localeScriptValidationEnabled = true;
    public String localeScriptViolationSeverity = "WARNING";

    // 이전 버전의 프로젝트 설정을 글로벌 설정으로 가져오기 위한 호환 필드.
    // 신규 AI 설정은 LocaleGridAiSettingsState에만 저장한다.
    public boolean llmEnabled = false;
    public String llmEndpoint = "http://localhost:8000/v1/chat/completions";
    public String llmModel = "qwen3.6-27b";
    public String llmApiKey = "";
    public int llmTimeoutSeconds = 30;
    public double llmTemperature = 0.2;

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

    public List<String> getExceptionKeyList() {
        List<String> keys = splitCsv(exceptionKeys);
        return keys.isEmpty() ? List.of("__section__") : keys;
    }

    public boolean isExceptionKey(String key) {
        return getExceptionKeyList().contains(key);
    }

    public void setExceptionKeysFromCsv(String value) {
        List<String> keys = splitCsv(value);
        exceptionKeys = keys.isEmpty() ? "__section__" : String.join(",", keys);
    }

    public boolean isLocaleScriptViolationError() {
        return "ERROR".equalsIgnoreCase(localeScriptViolationSeverity);
    }

    private static List<String> splitCsv(String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return new ArrayList<>(result);
    }
}
