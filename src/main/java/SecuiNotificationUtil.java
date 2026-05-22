import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class SecuiNotificationUtil {
    private static final String NOTIFICATION_GROUP_ID = "secui.version.check";

    /**
     * 프로젝트에 알림을 표시합니다.
     *
     * @param project 프로젝트
     * @param title 알림 제목
     * @param message 알림 메시지
     * @param type 알림 타입 (INFORMATION, WARNING, ERROR)
     */
    public static void showNotification(@NotNull Project project, @NotNull String title, @NotNull String message, @NotNull NotificationType type) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, message, type)
            .notify(project);
    }

    /**
     * 프로젝트에 정보 알림을 표시합니다.
     *
     * @param project 프로젝트
     * @param title 알림 제목
     * @param message 알림 메시지
     */
    public static void showInfoNotification(@NotNull Project project, @NotNull String title, @NotNull String message) {
        showNotification(project, title, message, NotificationType.INFORMATION);
    }

    /**
     * 프로젝트에 경고 알림을 표시합니다.
     *
     * @param project 프로젝트
     * @param title 알림 제목
     * @param message 알림 메시지
     */
    public static void showWarningNotification(@NotNull Project project, @NotNull String title, @NotNull String message) {
        showNotification(project, title, message, NotificationType.WARNING);
    }

    /**
     * 프로젝트에 오류 알림을 표시합니다.
     *
     * @param project 프로젝트
     * @param title 알림 제목
     * @param message 알림 메시지
     */
    public static void showErrorNotification(@NotNull Project project, @NotNull String title, @NotNull String message) {
        showNotification(project, title, message, NotificationType.ERROR);
    }
} 