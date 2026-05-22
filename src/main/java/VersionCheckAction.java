import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

public class VersionCheckAction extends AnAction {
    private volatile boolean isRunning = false;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // 현재 프로젝트 가져오기
        Project project = e.getProject();
        if (project == null || isRunning) return;

        // 백그라운드에서 버전 확인 로직 실행
        isRunning = true;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Map<String, String> localVersions = VersionChecker.checkLocalModuleVersions(project);
                Map<String, String> latestVersions = VersionChecker.fetchLatestVersionsFromNexus(project);
                String message = VersionCheckUtil.getVersionCheckTableMessage(project);

                // UI 스레드에서 테이블 표시
                SwingUtilities.invokeLater(() -> {
                    isRunning = false;
                    if (message != null) {
                        SecuiVersionTable table = new SecuiVersionTable(project, localVersions, latestVersions, message);
                        table.show();
                    }
                });
            } catch (Exception ex) {
                isRunning = false;
                ex.printStackTrace();
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 프로젝트가 열려 있을 때만 버튼 활성화
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null);
    }
}