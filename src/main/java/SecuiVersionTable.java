import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.net.URISyntaxException;

public class SecuiVersionTable extends DialogWrapper {
    private final Project project;
    private Map<String, String> localVersions;
    private Map<String, String> latestVersions;
    private String message;
    private JTextField nexusUrlField;
    private JBTable table;
    private JLabel messageLabel;
    private JLabel loadingLabel;
    private Process currentProcess; // 현재 실행 중인 프로세스 추적
    private JButton updateButton;   // 업데이트/중단 버튼
    private JButton toggleOutputButton;
    private JButton applyButton;
    private boolean useYarn;
    private JTextArea outputArea;
    private JSplitPane splitPane;
    private JPanel rightPanel;
    private boolean isOutputVisible = false;
    private JPanel leftPanel;
    private int defaultDialogWidth = 600;
    private int defaultDialogHeight = 600;
    private JTextField consoleInputField;
    private JButton consoleSendButton;

    public SecuiVersionTable(Project project, Map<String, String> localVersions, Map<String, String> latestVersions, String message) {
        super(project);
        this.project = project;
        this.localVersions = localVersions;
        this.latestVersions = latestVersions;
        this.message = message;
        this.useYarn = new File(project.getBasePath(), "yarn.lock").exists();
        boolean isDevMode = Boolean.parseBoolean(System.getProperty("dev.mode"));
        String title = isDevMode ? "@vue 버전 확인" : "@secui 버전 확인";
        setTitle(title);
        init();
    }

    private void refreshData() {
        // 로딩 표시 시작
        loadingLabel.setVisible(true);
        
        // 백그라운드에서 데이터 새로고침
        SwingUtilities.invokeLater(() -> {
            try {
                // 현재 버전 정보와 최신 버전 정보 모두 재조회
                localVersions = VersionChecker.checkLocalModuleVersions(project);
                latestVersions = VersionChecker.fetchLatestVersionsFromNexus(project);
                message = VersionCheckUtil.getVersionCheckTableMessage(project);
                
                // 테이블 데이터 업데이트
                DefaultTableModel model = (DefaultTableModel) table.getModel();
                model.setRowCount(0);
                
                for (Map.Entry<String, String> entry : localVersions.entrySet()) {
                    String module = entry.getKey();
                    String localVersion = entry.getValue();
                    String latestVersion = latestVersions.get(module);
                    boolean needsUpdate = latestVersion != null && !latestVersion.equals(localVersion);
                    model.addRow(new Object[]{needsUpdate ? Boolean.TRUE : Boolean.FALSE, module, localVersion, latestVersion != null ? latestVersion : "-"});
                }
                
                // 메시지 업데이트
                messageLabel.setText(message);
                updateButton.setEnabled(hasSelectedModules());
            } finally {
                // 로딩 표시 종료
                loadingLabel.setVisible(false);
            }
        });
    }

    private boolean hasSelectedModules() {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        for (int i = 0; i < model.getRowCount(); i++) {
            if ((Boolean) model.getValueAt(i, 0)) {
                return true;
            }
        }
        return false;
    }

    private void updateSelectedModules() {
        List<String> selectedModules = new ArrayList<>();
        DefaultTableModel model = (DefaultTableModel) table.getModel();

        for (int i = 0; i < model.getRowCount(); i++) {
            if ((Boolean) model.getValueAt(i, 0)) {
                String module = (String) model.getValueAt(i, 1);
                String latestVersion = (String) model.getValueAt(i, 3);
                if (!latestVersion.equals("-")) {
                    selectedModules.add(module + "@" + latestVersion);
                }
            }
        }

        if (!selectedModules.isEmpty()) {
            String command = useYarn ? "yarn add " : "npm install ";
            command += String.join(" ", selectedModules);

            // 버튼 텍스트 변경 및 기능 전환
            updateButton.setText("업데이트 중단");
            updateButton.removeActionListener(updateButton.getActionListeners()[0]);
            updateButton.addActionListener(e -> cancelUpdate());

            showOutput();
            outputArea.setText(command + "\n\n");
            setControlsEnabled(false);

            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command("cmd", "/c", command);
                processBuilder.directory(new File(project.getBasePath()));
                processBuilder.redirectErrorStream(true);
                currentProcess = processBuilder.start();  // 여기가 중요!

                new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(currentProcess.getInputStream()))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (currentProcess == null) break;
                            final String finalLine = line;
                            SwingUtilities.invokeLater(() -> {
                                outputArea.append(finalLine + "\n");
                                outputArea.setCaretPosition(outputArea.getDocument().getLength());
                            });
                        }

                        if (currentProcess != null) {
                            currentProcess.waitFor();
                            SwingUtilities.invokeLater(this::completeUpdate);
                        }
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> handleUpdateError(e));
                    }
                }).start();
            } catch (Exception e) {
                handleUpdateError(e);
            }
        }
    }

    private void cancelUpdate() {
        if (currentProcess != null) {
            currentProcess.destroy(); // 프로세스 종료
            currentProcess = null;
            outputArea.append("\n업데이트가 사용자에 의해 중단되었습니다.\n");
        }
        resetUpdateButton();
        setControlsEnabled(true);
    }

    private void completeUpdate() {
        try {
            if (useYarn) {
                updateYarnLockResolvedUrls();
            }
            outputArea.append("\n업데이트가 완료되었습니다.\n");
            JOptionPane.showMessageDialog(this.getContentPane(),
                    "모듈 업데이트가 완료되었습니다.",
                    "업데이트 완료",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            outputArea.append("\nyarn.lock 파일 업데이트 중 오류: " + e.getMessage() + "\n");
        } finally {
            resetUpdateButton();
            refreshData();
            setControlsEnabled(true);
        }
    }

    private void handleUpdateError(Exception e) {
        outputArea.append("\n오류 발생: " + e.getMessage() + "\n");
        JOptionPane.showMessageDialog(this.getContentPane(),
                "모듈 업데이트 중 오류가 발생했습니다: " + e.getMessage(),
                "오류",
                JOptionPane.ERROR_MESSAGE);
        resetUpdateButton();
        setControlsEnabled(true);
    }

    private void resetUpdateButton() {
        updateButton.setText("모듈 업데이트");
        updateButton.removeActionListener(updateButton.getActionListeners()[0]);
        updateButton.addActionListener(e -> updateSelectedModules());
    }

    private void setControlsEnabled(boolean enabled) {
        table.setEnabled(enabled);
        nexusUrlField.setEnabled(enabled);
        getOKAction().setEnabled(enabled);
        applyButton.setEnabled(enabled);
        toggleOutputButton.setEnabled(enabled);
    }


    private void updateYarnLockResolvedUrls() throws IOException {
        Path yarnLockPath = Paths.get(project.getBasePath(), "yarn.lock");
        try {
            List<String> lines = Files.readAllLines(yarnLockPath, StandardCharsets.UTF_8);
            String nexusBase = SecuiSettingsState.getInstance(project).getNexusUrl();
            if (nexusBase == null || nexusBase.isEmpty()) return;
            if (!nexusBase.endsWith("/")) nexusBase += "/";

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                // @secui/ 패키지 블록 시작 확인
                boolean isDevMode = Boolean.parseBoolean(System.getProperty("dev.mode"));
                if (line.startsWith(isDevMode ?  "\"@vue/" : "\"@secui/") && line.trim().endsWith(":")) {
                    // 해당 블록 내 탐색
                    for (int j = i + 1; j < lines.size() && lines.get(j).startsWith(" "); j++) {
                        String innerLine = lines.get(j).trim();
                        if (innerLine.startsWith("resolved ")) {
                            String currentUrl = innerLine.substring("resolved ".length()).replace("\"", "");
                            if (!currentUrl.startsWith(nexusBase)) {
                                try {
                                    // Nexus 외의 URL이면 변경 수행
                                    int suffixIndex = currentUrl.indexOf(isDevMode ? "/@vue/": "/@secui/");
                                    String suffix;
                                    if (suffixIndex >= 0) {
                                        suffix = currentUrl.substring(suffixIndex);
                                    } else {
                                        URI uri = new URI(currentUrl);
                                        suffix = uri.getRawPath()
                                                + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "")
                                                + (uri.getRawFragment() != null ? "#" + uri.getRawFragment() : "");
                                    }
                                    String newUrl = nexusBase + suffix;
                                    String indent = lines.get(j).substring(0, lines.get(j).indexOf("resolved"));
                                    lines.set(j, indent + "resolved \"" + newUrl + "\"");
                                } catch (URISyntaxException e) {
                                    e.printStackTrace(); // 예외 출력 (혹은 로깅)
                                }
                            }
                        }
                    }
                }
            }

            // 변경된 내용을 yarn.lock에 다시 쓰기 (예: IntelliJ WriteCommandAction 내에서 실행)
            Files.write(yarnLockPath, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            outputArea.append("\nyarn.lock 파일 업데이트 중 오류 발생: " + e.getMessage() + "\n");
        }
    }

    private class CheckBoxOrEmptyRenderer extends JCheckBox implements TableCellRenderer {
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            String versionDisplay = (String) table.getModel().getValueAt(row, 2);
            String latestVersion = (String) table.getModel().getValueAt(row, 3);
            String localVersion = versionDisplay.split(" ")[0]; // "1.2.3 (1.2.2)" → "1.2.3"
            boolean needsUpdate = !latestVersion.equals("-") && !latestVersion.equals(localVersion);
            if (needsUpdate) {
                setSelected(Boolean.TRUE.equals(value));
                setEnabled(true);
                setHorizontalAlignment(CENTER);
                setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                return this;
            } else {
                JPanel empty = new JPanel();
                empty.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                return empty;
            }
        }
    }
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 왼쪽 패널 (URL, 드롭다운, 테이블, 메시지, 버튼)
        leftPanel = new JPanel();
        leftPanel.setLayout(new BorderLayout());

        // URL 입력 + 변경 버튼
        JPanel urlPanel = new JPanel(new BorderLayout(5, 0));
        urlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 입력 필드 설정
        nexusUrlField = new JTextField(SecuiSettingsState.getInstance(project).getNexusUrl());
        nexusUrlField.setFont(nexusUrlField.getFont().deriveFont(nexusUrlField.getFont().getSize() - 1f));
        nexusUrlField.setToolTipText("Nexus 서버 URL을 입력하세요");
        nexusUrlField.putClientProperty("JTextField.placeholderText", "Nexus 서버 URL을 입력하세요");
        
        // 버튼 설정
        applyButton = new JButton("변경");
        
        // 로딩 표시 레이블
        loadingLabel = new JLabel(AllIcons.Process.Step_1);
        loadingLabel.setVisible(false);
        
        // 버튼 크기 조정
        Dimension buttonSize = new Dimension(80, 35);
        applyButton.setPreferredSize(buttonSize);
        
        // 입력 필드 크기 조정
        nexusUrlField.setPreferredSize(new Dimension(Integer.MAX_VALUE, 35));
        
        applyButton.addActionListener(e -> {
            SecuiSettingsState.getInstance(project).setNexusUrl(nexusUrlField.getText());
            refreshData();
        });
        JPanel urlBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        urlBtnPanel.add(loadingLabel);
        urlBtnPanel.add(applyButton);
        urlPanel.add(nexusUrlField, BorderLayout.CENTER);
        urlPanel.add(urlBtnPanel, BorderLayout.EAST);
        leftPanel.add(urlPanel, BorderLayout.NORTH);

        // --- Lock 파일 버전 추출 메서드 추가 ---
        Map<String, String> lockFileVersions = new HashMap<>();
        Set<String> allModules = new HashSet<>(localVersions.keySet());
        File yarnLock = new File(project.getBasePath(), "yarn.lock");
        File packageLock = new File(project.getBasePath(), "package-lock.json");
        if (yarnLock.exists()) {
            lockFileVersions = getYarnLockVersions(yarnLock, allModules);
        } else if (packageLock.exists()) {
            lockFileVersions = getPackageLockVersions(packageLock, allModules);
        }

        // 테이블 모델 및 테이블 생성
        DefaultTableModel model = new DefaultTableModel() {
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) return Boolean.class;
                return String.class;
            }
            public boolean isCellEditable(int row, int column) {
                if (column == 0) {
                    // 실제 비교는 localVersion과 latestVersion으로!
                    String versionDisplay = (String) getValueAt(row, 2);
                    String latestVersion = (String) getValueAt(row, 3);
                    String localVersion = versionDisplay.split(" ")[0]; // "1.2.3 (1.2.2)" → "1.2.3"
                    return !latestVersion.equals("-") && !latestVersion.equals(localVersion);
                }
                return false;
            }
        };
        model.addColumn("선택");
        model.addColumn("모듈명");
        model.addColumn("현재 버전(Lock)");
        model.addColumn("최신 버전");
        for (Map.Entry<String, String> entry : localVersions.entrySet()) {
            String module = entry.getKey();
            String localVersion = entry.getValue();
            String lockVersion = lockFileVersions.getOrDefault(module, null);
            String latestVersion = latestVersions.get(module);
            boolean needsUpdate = latestVersion != null && !latestVersion.equals(localVersion);

            // lockVersion이 null 또는 "-"이면 괄호 없이, 있으면 "현재버전 (lock버전)" 형태로
            String versionDisplay = localVersion;
            if (lockVersion != null && !lockVersion.equals("-") && !lockVersion.equals(localVersion)) {
                versionDisplay += " (" + lockVersion + ")";
            }
            model.addRow(new Object[]{
                needsUpdate ? Boolean.TRUE : Boolean.FALSE,
                module,
                versionDisplay,
                latestVersion != null ? latestVersion : "-"
            });
        }

        // 테이블 생성
        table = new JBTable(model);
        table.setDefaultRenderer(Boolean.class, new CheckBoxOrEmptyRenderer());
        table.setStriped(true);
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.getColumnModel().getColumn(0).setPreferredWidth(20);
        table.getColumnModel().getColumn(1).setPreferredWidth(280);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(60);
        // 체크박스 변경 이벤트
        model.addTableModelListener(e -> {
            if (e.getColumn() == 0) {
                updateButton.setEnabled(hasSelectedModules());
            }
        });
        JBScrollPane tableScrollPane = new JBScrollPane(table);
        tableScrollPane.setPreferredSize(new Dimension(defaultDialogWidth - 140, defaultDialogHeight - 100));

        // 테이블 위 선택 버튼 부분
        JPanel selectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton selectAllBtn = new JButton("일괄 선택");
        JButton deselectAllBtn = new JButton("일괄 해제");
        toggleOutputButton = new JButton(isOutputVisible ? "콘솔 닫기" : "콘솔 열기");
        toggleOutputButton.addActionListener(e -> {
            if (isOutputVisible) {
                hideOutput();
                toggleOutputButton.setText("콘솔 열기");
            } else {
                showOutput();
                toggleOutputButton.setText("콘솔 닫기");
            }
        });
        selectAllBtn.setPreferredSize(new Dimension(90, 25));
        deselectAllBtn.setPreferredSize(new Dimension(90, 25));
        deselectAllBtn.addActionListener(e -> {
            for (int i = 0; i < model.getRowCount(); i++) model.setValueAt(false, i, 0);
            updateButton.setEnabled(false);
        });
        selectAllBtn.addActionListener(e -> {
            for (int i = 0; i < model.getRowCount(); i++) {
                String versionDisplay = (String) model.getValueAt(i, 2);
                String latestVersion = (String) model.getValueAt(i, 3);
                String localVersion = versionDisplay.split(" ")[0];
                if (!latestVersion.equals("-") && !latestVersion.equals(localVersion)) {
                    model.setValueAt(true, i, 0);
                }
            }
            updateButton.setEnabled(hasSelectedModules());
        });
        selectPanel.add(selectAllBtn);
        selectPanel.add(deselectAllBtn);
        selectPanel.add(toggleOutputButton);
        // 필요시 selectPanel.add(Box.createHorizontalStrut(10));

        // 테이블+버튼 합치기
        JPanel tableWithDropdown = new JPanel(new BorderLayout());
        tableWithDropdown.add(selectPanel, BorderLayout.NORTH);
        tableWithDropdown.add(tableScrollPane, BorderLayout.CENTER);
        leftPanel.add(tableWithDropdown, BorderLayout.CENTER);

        // 메시지, 버튼, 출력 토글
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        messageLabel = new JLabel(message);
        messagePanel.add(messageLabel, BorderLayout.CENTER);
        updateButton = new JButton("모듈 업데이트");
        updateButton.setEnabled(hasSelectedModules());
        updateButton.addActionListener(e -> updateSelectedModules());
        JPanel buttonPanel2 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel2.add(updateButton);
        messagePanel.add(buttonPanel2, BorderLayout.EAST);
        leftPanel.add(messagePanel, BorderLayout.SOUTH);

        // 오른쪽 패널 (대화형 콘솔)
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JBScrollPane outputScrollPane = new JBScrollPane(outputArea);

        // 대화형 입력 필드만 생성
        consoleInputField = new JTextField();

        JPanel consoleInputPanel = new JPanel(new BorderLayout(5, 0));
        consoleInputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        consoleInputPanel.add(consoleInputField, BorderLayout.CENTER);

        // 입력 필드에서 엔터 시 전송
        consoleInputField.addActionListener(e -> sendConsoleInput());

        // 콘솔 패널 조립
        JPanel consolePanel = new JPanel(new BorderLayout());
        consolePanel.add(outputScrollPane, BorderLayout.CENTER);
        consolePanel.add(consoleInputPanel, BorderLayout.SOUTH);

        rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(consolePanel, BorderLayout.CENTER);
        rightPanel.setVisible(false);

        // 분할 패널
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(0.5);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerSize(0);      // 드래그바 완전 제거
        splitPane.setEnabled(false);      // 마우스 조정 불가
        splitPane.setBorder(null);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // 다이얼로그 기본 크기 지정
        mainPanel.setPreferredSize(new Dimension(defaultDialogWidth, 500));
        
        // 콘솔 입력 필드 설정
        consoleInputField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                // Ctrl+C 입력 감지
                if (e.isControlDown() && e.getKeyCode() == java.awt.event.KeyEvent.VK_C) {
                    if (currentProcess != null) {
                        currentProcess.destroy();
                        currentProcess = null;
                        outputArea.append("\n[사용자에 의해 명령이 중단되었습니다.]\n");
                    }
                    // 입력 필드 비우기
                    consoleInputField.setText("");
                }
            }
        });
        
        return mainPanel;
    }

    protected Action[] createActions() {
        return new Action[]{getOKAction()};
    }

    private void showOutput() {
        if (!isOutputVisible) {
            // 팝업 너비 2배로
            Window window = SwingUtilities.getWindowAncestor(splitPane);
            if (window != null) {
                window.setSize(defaultDialogWidth * 2 - 35, defaultDialogHeight);
                window.validate();
            }

            isOutputVisible = true;
            splitPane.setDividerLocation(0.5);
            rightPanel.setVisible(true);
        }
    }

    private void hideOutput() {
        if (isOutputVisible) {
            isOutputVisible = false;
            rightPanel.setVisible(false);
            splitPane.setDividerLocation(1.0);
            // 팝업 너비 원래대로
            Window window = SwingUtilities.getWindowAncestor(splitPane);
            if (window != null) {
                window.setSize(defaultDialogWidth, defaultDialogHeight);
                window.validate();
            }
        }
    }

    // 2. 콘솔 입력 처리 메서드 추가
    private void sendConsoleInput() {
        String input = consoleInputField.getText();
        if (input == null || input.trim().isEmpty()) return;
        outputArea.append("> " + input + "\n");
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
        consoleInputField.setText("");

        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command("cmd", "/c", input);
            pb.directory(new File(project.getBasePath()));
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            new Thread(() -> {
                // 인코딩을 MS949로 변경 (윈도우 cmd 한글 지원)
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), java.nio.charset.Charset.forName("MS949")))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String finalLine = line;
                        SwingUtilities.invokeLater(() -> {
                            outputArea.append(finalLine + "\n");
                            outputArea.setCaretPosition(outputArea.getDocument().getLength());
                        });
                    }
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> {
                        outputArea.append("[오류] " + ex.getMessage() + "\n");
                    });
                }
            }).start();
        } catch (IOException ex) {
            outputArea.append("[실행 오류] " + ex.getMessage() + "\n");
        }
    }

    // yarn.lock에서 버전 추출
    private Map<String, String> getYarnLockVersions(File yarnLockFile, Set<String> modules) {
        Map<String, String> lockVersions = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(yarnLockFile.toPath(), StandardCharsets.UTF_8);
            String currentModule = null;
            for (String line : lines) {
                line = line.trim();
                if ((line.startsWith("\"@secui/") || line.startsWith("\"@vue/")) && line.endsWith(":")) {
                    int atIdx = line.indexOf("@", 2);
                    if (atIdx > 0) {
                        String moduleName = line.substring(1, atIdx);
                        if (modules.contains(moduleName)) {
                            currentModule = moduleName;
                        } else {
                            currentModule = null;
                        }
                    }
                } else if (currentModule != null && line.startsWith("version ")) {
                    String version = line.substring("version ".length()).replaceAll("\"", "");
                    lockVersions.put(currentModule, version);
                    currentModule = null;
                }
            }
        } catch (IOException e) {
            // 무시
        }
        return lockVersions;
    }

    // package-lock.json에서 버전 추출 (node_modules/ 경로 우선)
    private Map<String, String> getPackageLockVersions(File packageLockFile, Set<String> modules) {
        Map<String, String> lockVersions = new HashMap<>();
        try {
            String json = new String(Files.readAllBytes(packageLockFile.toPath()), StandardCharsets.UTF_8);
            for (String module : modules) {
                // 1. node_modules/패키지명 우선 검색
                String key1 = "\"node_modules/" + module + "\":";
                int idx1 = json.indexOf(key1);
                int idx = -1;
                if (idx1 > 0) {
                    idx = idx1;
                } else {
                    // 2. packages/ 하위 경로도 검색
                    String key2 = "\"packages/";
                    int searchFrom = 0;
                    while ((searchFrom = json.indexOf(key2, searchFrom)) != -1) {
                        int end = json.indexOf("\":", searchFrom);
                        if (end > 0) {
                            String path = json.substring(searchFrom + 1, end); // packages/...
                            if (path.endsWith("/" + module)) {
                                idx = searchFrom;
                                break;
                            }
                        }
                        searchFrom = end > 0 ? end : searchFrom + key2.length();
                    }
                }
                // 3. 루트(기존 방식)도 마지막으로 검색
                if (idx == -1) {
                    String key3 = "\"" + module + "\":";
                    int idx3 = json.indexOf(key3);
                    if (idx3 > 0) idx = idx3;
                }
                // 버전 추출
                if (idx > 0) {
                    int verIdx = json.indexOf("\"version\"", idx);
                    if (verIdx > 0) {
                        int colon = json.indexOf(":", verIdx);
                        int quote1 = json.indexOf("\"", colon);
                        int quote2 = json.indexOf("\"", quote1 + 1);
                        if (quote1 > 0 && quote2 > quote1) {
                            String version = json.substring(quote1 + 1, quote2);
                            lockVersions.put(module, version);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // 무시
        }
        return lockVersions;
    }
}