import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class VersionChecker {

    // 1. node_modules/@secui 하위의 모듈 이름과 버전을 모두 가져와 목록화
    public static Map<String, String> checkLocalModuleVersions(Project project) {
        Map<String, String> localVersions = new HashMap<>();
        File baseDir = new File(project.getBasePath());
        File nodeModulesDir = new File(baseDir, "node_modules");
        boolean isDevMode = Boolean.parseBoolean(System.getProperty("dev.mode"));
        File secuiDir = new File(nodeModulesDir, isDevMode ? "@vue" : "@secui");

        if (secuiDir.exists() && secuiDir.isDirectory()) {
            for (File moduleDir : secuiDir.listFiles()) {
                if (moduleDir.isDirectory()) {
                    File packageJson = new File(moduleDir, "package.json");
                    if (packageJson.exists()) {
                        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(packageJson))) {
                            StringBuilder content = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                content.append(line);
                            }
                            // package.json에서 이름, 버전 정보 추출
                            JSONObject packageJsonObj = new JSONObject(content.toString());
                            String pName = packageJsonObj.getString("name");
                            String pVersion = packageJsonObj.getString("version");
                            localVersions.put(pName, pVersion);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return localVersions;
    }

    // 2. Nexus 서버 또는 NPM 레지스트리와 통신하여 dist-tags.latest로 최신 버전 정보를 가져옴
    public static Map<String, String> fetchLatestVersionsFromNexus(Project project) {
        Map<String, String> localVersions = checkLocalModuleVersions(project);
        Map<String, String> latestVersions = new ConcurrentHashMap<>();
        String registryUrl = SecuiSettingsState.getInstance(project).getNexusUrl();

        // 병렬 처리
        int poolSize = Math.max(1, Math.min(10, localVersions.size()));
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        List<Future<?>> futures = new ArrayList<>();

        for (Map.Entry<String, String> entry : localVersions.entrySet()) {
            String moduleName = entry.getKey();
            futures.add(executor.submit(() -> {
                try {
                    URL url = new URL(registryUrl + "/" + moduleName);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(2000);
                    connection.setReadTimeout(5000);

                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()))) {
                            String response = reader.lines().collect(Collectors.joining());
                            String latestVersion = new JSONObject(response)
                                    .getJSONObject("dist-tags")
                                    .getString("latest");
                            latestVersions.put(moduleName, latestVersion);
                        }
                    }
                    connection.disconnect();
                } catch (Exception e) {
                    System.err.println("Error fetching " + moduleName + ": " + e.getMessage());
                }
            }));
        }

        // 모든 작업 완료 대기
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
                System.err.println("Thread interrupted: " + e.getMessage());
            }
        }
        executor.shutdown();
        return latestVersions;
    }
}