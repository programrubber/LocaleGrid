import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SecuiProjectListener implements ProjectManagerListener {
    private static final Set<Project> notifiedProjects = ConcurrentHashMap.newKeySet(); // 프로젝트별 중복 방지

    @Override
    public void projectOpened(@NotNull Project project) {
        SecuiSettingsState settingsState = SecuiSettingsState.getInstance(project);
        if (settingsState.getNotificationEnabled()) {
            if (notifiedProjects.add(project)) { // 첫 실행된 프로젝트만 추가됨
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    String message = VersionCheckUtil.getVersionCheckMessage(project);
                    SwingUtilities.invokeLater(() -> {
                        if (message != null) {
                            boolean isDevMode = Boolean.parseBoolean(System.getProperty("dev.mode"));
                            String title = isDevMode ? "@vue 버전 확인" : "@secui 버전 확인";
                            SecuiNotificationUtil.showInfoNotification(project, title, message);
                        }
                    });
                });
            }
        }
    }
}
