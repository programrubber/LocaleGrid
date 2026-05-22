import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.Map;

public class VersionCheckUtil {

    // 버전 확인 로직을 공통 메서드로 분리
    public static boolean checkVersions(Project project, boolean isProjectStart) {
        // 1. node_modules/@secui 하위의 모듈 이름과 버전을 모두 가져와 목록화
        Map<String, String> localVersions = VersionChecker.checkLocalModuleVersions(project);

        // 2. Nexus 서버와 통신하여 최신 버전 정보를 가져옴
        Map<String, String> latestVersions = VersionChecker.fetchLatestVersionsFromNexus(project);

        // 3. 버전 비교 및 결과 메시지 생성
        ArrayList<String> msgList = new ArrayList<>();
        for (Map.Entry<String, String> entry : localVersions.entrySet()) {
            String module = entry.getKey();
            String localVersion = entry.getValue();
            String latestVersion = latestVersions.get(module);

            if (latestVersion != null && !latestVersion.equals(localVersion)) {
                msgList.add(module + " (현재: " + localVersion + ", 최신: " + latestVersion + ")");
            }
        }

        // 프로젝트에 @secui 모듈이 없는 경우
        if (localVersions.isEmpty()) {
            if (isProjectStart) {
                // 프로젝트 시작 시에는 메시지를 반환하지 않고, 업데이트 필요 없음을 반환
                return false;
            }
            return false; // 프로젝트 시작이 아닌 경우에도 업데이트 필요 없음
        }

        // Nexus 서버로부터 버전을 확인하지 못한 경우
        if (latestVersions.isEmpty()) {
            return false; // 업데이트 필요 없음 (에러 상황)
        }

        // 업데이트가 필요한 모듈이 있는 경우
        return !msgList.isEmpty(); // 업데이트가 필요하면 true, 아니면 false 반환
    }

    // 결과 메시지를 생성하는 메서드
    public static String getVersionCheckMessage(Project project) {
        Map<String, String> localVersions = VersionChecker.checkLocalModuleVersions(project);
        Map<String, String> latestVersions = VersionChecker.fetchLatestVersionsFromNexus(project);

        ArrayList<String> msgList = new ArrayList<>();
        for (Map.Entry<String, String> entry : localVersions.entrySet()) {
            String module = entry.getKey();
            String localVersion = entry.getValue();
            String latestVersion = latestVersions.get(module);

            if (latestVersion != null && !latestVersion.equals(localVersion)) {
                msgList.add(module + " (현재: " + localVersion + ", 최신: " + latestVersion + ")");
            }
        }

        if (localVersions.isEmpty()) {
            return "@secui 모듈이 존재하지 않습니다.";
        }

        if (latestVersions.isEmpty()) {
            return "Nexus 서버로부터 버전을 확인하지 못하였습니다. (" + SecuiSettingsState.getInstance(project).getNexusUrl() + ")";
        }

        if (msgList.isEmpty()) {
            return "@secui 모듈이 최신 버전입니다.";
        }

        return String.join("\n", msgList);
    }

    // 테이블용 메시지를 생성하는 메서드
    public static String getVersionCheckTableMessage(Project project) {
        Map<String, String> localVersions = VersionChecker.checkLocalModuleVersions(project);
        Map<String, String> latestVersions = VersionChecker.fetchLatestVersionsFromNexus(project);

        if (localVersions.isEmpty()) {
            return "@secui 모듈이 존재하지 않습니다.";
        }

        if (latestVersions.isEmpty()) {
            return "Nexus 서버로부터 버전을 확인하지 못하였습니다. (" + SecuiSettingsState.getInstance(project).getNexusUrl() + ")";
        }

        boolean hasUpdates = false;
        for (Map.Entry<String, String> entry : localVersions.entrySet()) {
            String module = entry.getKey();
            String localVersion = entry.getValue();
            String latestVersion = latestVersions.get(module);

            if (latestVersion != null && !latestVersion.equals(localVersion)) {
                hasUpdates = true;
                break;
            }
        }

        if (!hasUpdates) {
            return "@secui 모듈이 최신 버전입니다.";
        }

        return "업데이트가 필요한 모듈이 있습니다.";
    }
}