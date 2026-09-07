package com.localegrid.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** IDE의 모든 프로젝트에서 공유하는 AI 번역 연결 설정. */
@State(name = "LocaleGridAiSettings", storages = @Storage("localeGridAi.xml"))
public class LocaleGridAiSettingsState implements PersistentStateComponent<LocaleGridAiSettingsState> {
    public boolean initialized = false;
    public boolean llmEnabled = false;
    public String llmEndpoint = "http://localhost:8000/v1/chat/completions";
    public String llmModel = "qwen3.6-27b";
    public String llmApiKey = "";
    public int llmTimeoutSeconds = 30;
    public double llmTemperature = 0.2;

    public static LocaleGridAiSettingsState getInstance(Project project) {
        LocaleGridAiSettingsState settings = ApplicationManager.getApplication()
            .getService(LocaleGridAiSettingsState.class);
        settings.migrateFrom(LocaleGridSettingsState.getInstance(project));
        return settings;
    }

    // 기본값뿐인 프로젝트는 이관 완료로 표시하지 않는다.
    synchronized void migrateFrom(LocaleGridSettingsState legacy) {
        if (initialized || !hasCustomSettings(legacy)) {
            return;
        }
        llmEnabled = legacy.llmEnabled;
        llmEndpoint = legacy.llmEndpoint;
        llmModel = legacy.llmModel;
        llmApiKey = legacy.llmApiKey;
        llmTimeoutSeconds = legacy.llmTimeoutSeconds;
        llmTemperature = legacy.llmTemperature;
        initialized = true;
    }

    private static boolean hasCustomSettings(LocaleGridSettingsState legacy) {
        LocaleGridSettingsState defaults = new LocaleGridSettingsState();
        return legacy.llmEnabled != defaults.llmEnabled
            || !Objects.equals(legacy.llmEndpoint, defaults.llmEndpoint)
            || !Objects.equals(legacy.llmModel, defaults.llmModel)
            || !Objects.equals(legacy.llmApiKey, defaults.llmApiKey)
            || legacy.llmTimeoutSeconds != defaults.llmTimeoutSeconds
            || Double.compare(legacy.llmTemperature, defaults.llmTemperature) != 0;
    }

    @Override
    public LocaleGridAiSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull LocaleGridAiSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public String getNormalizedLlmEndpoint() {
        if (llmEndpoint == null || llmEndpoint.trim().isEmpty()) {
            return "http://localhost:8000/v1/chat/completions";
        }
        return llmEndpoint.trim();
    }

    public String getNormalizedLlmModel() {
        if (llmModel == null || llmModel.trim().isEmpty()) {
            return "qwen3.6-27b";
        }
        return llmModel.trim();
    }

}
