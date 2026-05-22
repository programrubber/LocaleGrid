// 설정 상태 관리: SecuiSettingsState 클래스는 Nexus 서버 경로와 같은 설정 값을 영구적으로 저장

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.xmlb.XmlSerializerUtil;


import com.intellij.openapi.project.Project;

// .idea/secui-version-checker.xml 위치에 설정이 저장됨
@State(
        name = "SecuiSettingsState",
        storages = @Storage("secuiVersionChecker.xml")
)
public class SecuiSettingsState implements PersistentStateComponent<SecuiSettingsState> {
    private Boolean notificationEnabled = true; // 프로젝트 시작시 알림 기능 활성화 상태를 저장할 필드
    boolean isDevMode = Boolean.parseBoolean(System.getProperty("dev.mode"));
    private String nexusUrl = isDevMode ? "https://registry.npmjs.org" : "http://192.168.216.223:8333/repository/npm-host";

    public static SecuiSettingsState getInstance() {
        return new SecuiSettingsState();
    }

    public static SecuiSettingsState getInstance(Project project) {
        if (project == null) {
            throw new IllegalArgumentException("Project cannot be null");
        }
        SecuiSettingsState instance = project.getService(SecuiSettingsState.class);
        if (instance == null) {
            return new SecuiSettingsState();
//            throw new IllegalStateException("SecuiSettingsState is not initialized for the project");
        }
        return instance;
    }

    @Nullable
    @Override
    public SecuiSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull SecuiSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public String getNexusUrl() {
        return nexusUrl;
    }

    public void setNexusUrl(String nexusUrl) {
        this.nexusUrl = nexusUrl;
    }

    public Boolean getNotificationEnabled() {
        return notificationEnabled;
    }

    public void setNotificationEnabled(Boolean notificationEnabled) {
        this.notificationEnabled = notificationEnabled;
    }
}
