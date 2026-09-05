package com.localegrid.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HideableTitledPanel;
import com.intellij.ui.JBColor;
import com.localegrid.editor.LocaleGridFileEditor;
import com.localegrid.llm.LocaleGridLlmClient;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class LocaleGridSettingsConfigurable implements Configurable {
    private static final String SCRIPT_WARNING_LABEL = "경고 (저장 가능)";
    private static final String SCRIPT_ERROR_LABEL = "에러 (저장 차단)";

    private final Project project;
    private final LocaleGridSettingsState state;
    private JTextField localesRootField;
    private com.intellij.ui.components.JBTextField manualLocalesField;
    private JTextField exceptionKeysField;
    private JComboBox<Integer> indentComboBox;
    private JCheckBox localeScriptValidationCheckBox;
    private JComboBox<String> localeScriptSeverityComboBox;

    private JCheckBox llmEnabledCheckBox;
    private JTextField llmEndpointField;
    private JTextField llmModelField;
    private JPasswordField llmApiKeyField;
    private JComboBox<Integer> llmTimeoutComboBox;
    private JButton testConnectionButton;
    private JLabel testConnectionResultLabel;

    public LocaleGridSettingsConfigurable(Project project) {
        this.project = project;
        this.state = LocaleGridSettingsState.getInstance(project);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "LocaleGrid";
    }

    @Override
    public @Nullable JComponent createComponent() {
        JPanel wrapper = new JPanel(new BorderLayout());
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 0, 8, 0);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        // 1. 기본 설정 타이틀
        JLabel mainTitle = new JLabel("기본 설정");
        mainTitle.setFont(mainTitle.getFont().deriveFont(Font.BOLD, 14f));
        c.gridy = 0;
        c.gridx = 0;
        c.gridwidth = 2;
        panel.add(mainTitle, c);

        // 2. 기본 설정 설명
        JLabel mainDesc = new JLabel("locale JSON 파일을 찾고, 다국어 에디터의 locale 컬럼 순서를 정합니다.");
        mainDesc.setForeground(com.intellij.util.ui.UIUtil.getContextHelpForeground());
        c.gridy = 1;
        c.insets = new Insets(0, 0, 16, 0);
        panel.add(mainDesc, c);

        // 3. 입력 필드 초기화 및 래핑
        localesRootField = new JTextField(state.localesRoot, 32);
        manualLocalesField = new com.intellij.ui.components.JBTextField(state.manualLocales, 32);
        manualLocalesField.getEmptyText().setText("ko,en,ja,vi");

        JComponent localesRootWrapper = createFieldWithHint(localesRootField,
            "프로젝트 기준 상대 경로입니다. 예: locales , src/locales");
        JComponent manualLocalesWrapper = createFieldWithHint(manualLocalesField,
            "<html>비워두면 locale 루트 아래 디렉터리를 자동 감지합니다.<br>쉼표로 입력하면 그 순서대로 컬럼을 표시합니다. (예: ko,en,ja,vi)</html>");

        c.gridwidth = 1;
        c.insets = new Insets(8, 0, 8, 12);
        addRow(panel, c, 2, "locale 루트 경로", localesRootWrapper);
        addRow(panel, c, 3, "locale 표시 순서", manualLocalesWrapper);

        // 4. 구분 가로선
        c.gridy = 4;
        c.gridx = 0;
        c.gridwidth = 2;
        c.insets = new Insets(16, 0, 16, 0);
        panel.add(new JSeparator(JSeparator.HORIZONTAL), c);

        // 5. 고급 설정 내부 패널 구성
        JPanel advancedContent = new JPanel(new GridBagLayout());
        advancedContent.setOpaque(false);
        advancedContent.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        GridBagConstraints ac = new GridBagConstraints();
        ac.insets = new Insets(8, 0, 8, 12);
        ac.anchor = GridBagConstraints.WEST;
        ac.fill = GridBagConstraints.HORIZONTAL;

        exceptionKeysField = new JTextField(String.join(", ", state.getExceptionKeyList()), 32);
        JComponent exceptionKeysWrapper = createFieldWithHint(exceptionKeysField,
            "<html>번역 항목에서 제외할 최상위 키를 쉼표로 구분해 입력하세요. (예: __section__, __comment__)<br>예외키는 중복될 수 있으며, 저장 시 각 Locale 파일에서의 위치를 유지합니다.</html>");

        indentComboBox = new JComboBox<>(new Integer[]{2, 4});
        indentComboBox.setSelectedItem(state.jsonIndent);
        // 콤보박스가 가로로 꽉 차서 어색하지 않도록 왼쪽 정렬된 래퍼 패널 이용
        JPanel indentComboPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        indentComboPanel.setOpaque(false);
        indentComboPanel.add(indentComboBox);
        JComponent indentWrapper = createFieldWithHint(indentComboPanel,
            "적용 시 저장되는 JSON 들여쓰기 칸 수입니다. 기본값은 2입니다.");

        localeScriptValidationCheckBox = new JCheckBox("검사 사용", state.localeScriptValidationEnabled);
        JComponent localeScriptValidationWrapper = createFieldWithHint(
            localeScriptValidationCheckBox,
            "<html>ko, en, ja, vi는 내장 규칙으로, 그 외 표준 locale은 CLDR의 예상 문자 체계로 검사합니다.<br>숫자, 문장부호, 이모지와 라틴 문자는 허용합니다.</html>"
        );

        localeScriptSeverityComboBox = new JComboBox<>(new String[]{SCRIPT_WARNING_LABEL, SCRIPT_ERROR_LABEL});
        localeScriptSeverityComboBox.setSelectedItem(
            state.isLocaleScriptViolationError() ? SCRIPT_ERROR_LABEL : SCRIPT_WARNING_LABEL
        );
        localeScriptValidationCheckBox.addActionListener(event -> updateScriptSeverityEnabled());
        updateScriptSeverityEnabled();
        JPanel localeScriptSeverityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        localeScriptSeverityPanel.setOpaque(false);
        localeScriptSeverityPanel.add(localeScriptSeverityComboBox);
        JComponent localeScriptSeverityWrapper = createFieldWithHint(
            localeScriptSeverityPanel,
            "경고는 저장할 수 있고, 에러는 허용되지 않은 문자가 남아 있으면 저장을 차단합니다."
        );

        ac.gridwidth = 1;
        ac.gridy = 0;
        ac.gridx = 0;
        ac.weightx = 0;
        advancedContent.add(new JLabel("예외 키"), ac);
        ac.gridx = 1;
        ac.weightx = 1.0;
        advancedContent.add(exceptionKeysWrapper, ac);

        ac.gridy = 1;
        ac.gridx = 0;
        ac.weightx = 0;
        advancedContent.add(new JLabel("JSON 들여쓰기"), ac);
        ac.gridx = 1;
        ac.weightx = 1.0;
        advancedContent.add(indentWrapper, ac);

        ac.gridy = 2;
        ac.gridx = 0;
        ac.weightx = 0;
        advancedContent.add(new JLabel("문자 체계 검사"), ac);
        ac.gridx = 1;
        ac.weightx = 1.0;
        advancedContent.add(localeScriptValidationWrapper, ac);

        ac.gridy = 3;
        ac.gridx = 0;
        ac.weightx = 0;
        advancedContent.add(new JLabel("문자 위반 처리"), ac);
        ac.gridx = 1;
        ac.weightx = 1.0;
        advancedContent.add(localeScriptSeverityWrapper, ac);

        // 6. 고급 설정 접이식 패널 (HideableTitledPanel)
        HideableTitledPanel advancedPanel = new HideableTitledPanel("고급 설정", advancedContent, true);
        
        c.gridy = 5;
        c.gridx = 0;
        c.gridwidth = 2;
        c.insets = new Insets(0, 0, 8, 0);
        panel.add(advancedPanel, c);

        // 7. 사내 AI 번역 제안 (LLM 연동) 패널 구성
        JPanel aiContent = new JPanel(new GridBagLayout());
        aiContent.setOpaque(false);
        aiContent.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        GridBagConstraints aic = new GridBagConstraints();
        aic.insets = new Insets(8, 0, 8, 12);
        aic.anchor = GridBagConstraints.WEST;
        aic.fill = GridBagConstraints.HORIZONTAL;

        llmEnabledCheckBox = new JCheckBox("AI 번역 제안 활성화", state.llmEnabled);
        JComponent llmEnabledWrapper = createFieldWithHint(llmEnabledCheckBox,
            "하단 상세 패널에서 키명 옆의 [✨ AI 번역 제안] 버튼을 통해 번역 문구를 추천받습니다.");

        llmEndpointField = new JTextField(state.llmEndpoint, 32);
        JComponent llmEndpointWrapper = createFieldWithHint(llmEndpointField,
            "OpenAI 호환 Chat Completion 엔드포인트 URL (예: http://localhost:8000/v1/chat/completions)");

        llmModelField = new JTextField(state.llmModel, 32);
        JComponent llmModelWrapper = createFieldWithHint(llmModelField,
            "호스팅 중인 모델 식별자 (예: qwen3.6-27b, deepseek-v3, llama-3.3, gpt-4o-mini)");

        llmApiKeyField = new JPasswordField(state.llmApiKey, 32);
        JComponent llmApiKeyWrapper = createFieldWithHint(llmApiKeyField,
            "사내 인증 토큰 또는 API Key (필요 없는 경우 비워둡니다)");

        llmTimeoutComboBox = new JComboBox<>(new Integer[]{10, 30, 60, 120});
        llmTimeoutComboBox.setSelectedItem(state.llmTimeoutSeconds);
        JPanel timeoutPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        timeoutPanel.setOpaque(false);
        timeoutPanel.add(llmTimeoutComboBox);
        JComponent llmTimeoutWrapper = createFieldWithHint(timeoutPanel,
            "초 단위 응답 대기 시간입니다. 기본값은 30초입니다.");

        testConnectionButton = new JButton("연결 테스트");
        testConnectionResultLabel = new JLabel("");
        testConnectionResultLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        testPanel.setOpaque(false);
        testPanel.add(testConnectionButton);
        testPanel.add(testConnectionResultLabel);
        testConnectionButton.addActionListener(e -> performTestConnection());

        llmEnabledCheckBox.addActionListener(e -> updateLlmFieldsEnabled());

        aic.gridwidth = 1;
        aic.gridy = 0;
        aic.gridx = 0;
        aic.weightx = 0;
        aiContent.add(new JLabel("기능 활성화"), aic);
        aic.gridx = 1;
        aic.weightx = 1.0;
        aiContent.add(llmEnabledWrapper, aic);

        aic.gridy = 1;
        aic.gridx = 0;
        aic.weightx = 0;
        aiContent.add(new JLabel("엔드포인트 URL"), aic);
        aic.gridx = 1;
        aic.weightx = 1.0;
        aiContent.add(llmEndpointWrapper, aic);

        aic.gridy = 2;
        aic.gridx = 0;
        aic.weightx = 0;
        aiContent.add(new JLabel("모델 식별자"), aic);
        aic.gridx = 1;
        aic.weightx = 1.0;
        aiContent.add(llmModelWrapper, aic);

        aic.gridy = 3;
        aic.gridx = 0;
        aic.weightx = 0;
        aiContent.add(new JLabel("API Key"), aic);
        aic.gridx = 1;
        aic.weightx = 1.0;
        aiContent.add(llmApiKeyWrapper, aic);

        aic.gridy = 4;
        aic.gridx = 0;
        aic.weightx = 0;
        aiContent.add(new JLabel("타임아웃(초)"), aic);
        aic.gridx = 1;
        aic.weightx = 1.0;
        aiContent.add(llmTimeoutWrapper, aic);

        aic.gridy = 5;
        aic.gridx = 0;
        aic.weightx = 0;
        aiContent.add(new JLabel("연결 확인"), aic);
        aic.gridx = 1;
        aic.weightx = 1.0;
        aiContent.add(testPanel, aic);

        updateLlmFieldsEnabled();

        HideableTitledPanel aiPanel = new HideableTitledPanel("사내 AI 번역 제안 (LLM 연동)", aiContent, true);

        c.gridy = 6;
        c.gridx = 0;
        c.gridwidth = 2;
        c.insets = new Insets(0, 0, 8, 0);
        panel.add(aiPanel, c);

        wrapper.add(panel, BorderLayout.NORTH);
        return wrapper;
    }

    private JComponent createFieldWithHint(JComponent field, String hint) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.add(field, BorderLayout.CENTER);
        if (hint != null && !hint.isEmpty()) {
            JLabel hintLabel = new JLabel(hint);
            hintLabel.setForeground(com.intellij.util.ui.UIUtil.getContextHelpForeground());
            Font font = hintLabel.getFont();
            hintLabel.setFont(font.deriveFont(font.getSize2D() - 1f));
            panel.add(hintLabel, BorderLayout.SOUTH);
        }
        return panel;
    }

    private static void addRow(JPanel panel, GridBagConstraints c, int row, String label, JComponent component) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(component, c);
    }

    @Override
    public boolean isModified() {
        Integer selectedIndent = (Integer) indentComboBox.getSelectedItem();
        Integer selectedTimeout = (Integer) llmTimeoutComboBox.getSelectedItem();
        boolean llmModified = llmEnabledCheckBox.isSelected() != state.llmEnabled
            || !llmEndpointField.getText().trim().equals(state.llmEndpoint)
            || !llmModelField.getText().trim().equals(state.llmModel)
            || !new String(llmApiKeyField.getPassword()).equals(state.llmApiKey)
            || (selectedTimeout != null && selectedTimeout != state.llmTimeoutSeconds);

        return !localesRootField.getText().equals(state.localesRoot)
            || !manualLocalesField.getText().equals(state.manualLocales)
            || !normalizeExceptionKeys(exceptionKeysField.getText()).equals(String.join(",", state.getExceptionKeyList()))
            || (selectedIndent != null && selectedIndent != state.jsonIndent)
            || localeScriptValidationCheckBox.isSelected() != state.localeScriptValidationEnabled
            || !selectedScriptSeverity().equals(normalizeScriptSeverity(state.localeScriptViolationSeverity))
            || llmModified;
    }

    @Override
    public void apply() throws ConfigurationException {
        String nextLocalesRoot = localesRootField.getText().trim().isEmpty()
            ? "locales"
            : localesRootField.getText().trim();
        String nextManualLocales = manualLocalesField.getText().trim();
        LocaleGridSettingsState normalizedNextState = new LocaleGridSettingsState();
        normalizedNextState.localesRoot = nextLocalesRoot;
        normalizedNextState.manualLocales = nextManualLocales;
        normalizedNextState.setExceptionKeysFromCsv(exceptionKeysField.getText());

        boolean structuralSettingsChanged = !StructuralSettingsSnapshot.capture(state)
            .equals(StructuralSettingsSnapshot.capture(normalizedNextState));
        if (structuralSettingsChanged && hasModifiedLocaleGridEditor()) {
            throw new ConfigurationException(
                "열린 다국어 에디터에 저장되지 않은 변경 사항이 있습니다. "
                    + "변경 사항을 먼저 적용하거나 취소한 뒤 locale 루트, 표시 순서 또는 예외 키 설정을 변경하세요."
            );
        }

        state.localesRoot = nextLocalesRoot;
        state.manualLocales = nextManualLocales;
        state.exceptionKeys = normalizedNextState.exceptionKeys;
        Integer selectedIndent = (Integer) indentComboBox.getSelectedItem();
        if (selectedIndent != null) {
            state.jsonIndent = selectedIndent;
        }
        state.localeScriptValidationEnabled = localeScriptValidationCheckBox.isSelected();
        state.localeScriptViolationSeverity = selectedScriptSeverity();

        state.llmEnabled = llmEnabledCheckBox.isSelected();
        state.llmEndpoint = llmEndpointField.getText().trim().isEmpty()
            ? "http://localhost:8000/v1/chat/completions"
            : llmEndpointField.getText().trim();
        state.llmModel = llmModelField.getText().trim().isEmpty()
            ? "qwen3.6-27b"
            : llmModelField.getText().trim();
        state.llmApiKey = new String(llmApiKeyField.getPassword()).trim();
        Integer selectedTimeout = (Integer) llmTimeoutComboBox.getSelectedItem();
        if (selectedTimeout != null) {
            state.llmTimeoutSeconds = selectedTimeout;
        }

        project.getMessageBus()
            .syncPublisher(LocaleGridSettingsListener.TOPIC)
            .settingsChanged(structuralSettingsChanged);
    }

    @Override
    public void reset() {
        localesRootField.setText(state.localesRoot);
        manualLocalesField.setText(state.manualLocales);
        exceptionKeysField.setText(String.join(", ", state.getExceptionKeyList()));
        indentComboBox.setSelectedItem(state.jsonIndent);
        localeScriptValidationCheckBox.setSelected(state.localeScriptValidationEnabled);
        localeScriptSeverityComboBox.setSelectedItem(
            state.isLocaleScriptViolationError() ? SCRIPT_ERROR_LABEL : SCRIPT_WARNING_LABEL
        );
        updateScriptSeverityEnabled();

        llmEnabledCheckBox.setSelected(state.llmEnabled);
        llmEndpointField.setText(state.llmEndpoint);
        llmModelField.setText(state.llmModel);
        llmApiKeyField.setText(state.llmApiKey);
        llmTimeoutComboBox.setSelectedItem(state.llmTimeoutSeconds);
        testConnectionResultLabel.setText("");
        updateLlmFieldsEnabled();
    }

    private void updateLlmFieldsEnabled() {
        boolean enabled = llmEnabledCheckBox != null && llmEnabledCheckBox.isSelected();
        if (llmEndpointField != null) llmEndpointField.setEnabled(enabled);
        if (llmModelField != null) llmModelField.setEnabled(enabled);
        if (llmApiKeyField != null) llmApiKeyField.setEnabled(enabled);
        if (llmTimeoutComboBox != null) llmTimeoutComboBox.setEnabled(enabled);
        if (testConnectionButton != null) testConnectionButton.setEnabled(enabled);
    }

    private void performTestConnection() {
        testConnectionButton.setEnabled(false);
        testConnectionResultLabel.setForeground(com.intellij.util.ui.UIUtil.getContextHelpForeground());
        testConnectionResultLabel.setText("연결 테스트 중...");

        String endpoint = llmEndpointField.getText().trim();
        String model = llmModelField.getText().trim();
        String apiKey = new String(llmApiKeyField.getPassword()).trim();
        Integer timeout = (Integer) llmTimeoutComboBox.getSelectedItem();
        int timeoutSec = timeout != null ? timeout : 10;

        LocaleGridLlmClient.getInstance().testConnection(endpoint, model, apiKey, timeoutSec)
            .thenAccept(elapsedMs -> SwingUtilities.invokeLater(() -> {
                testConnectionButton.setEnabled(llmEnabledCheckBox.isSelected());
                testConnectionResultLabel.setForeground(new JBColor(new Color(0, 140, 50), new Color(80, 200, 100)));
                testConnectionResultLabel.setText("✓ 연결 성공 (" + elapsedMs + "ms)");
            }))
            .exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    testConnectionButton.setEnabled(llmEnabledCheckBox.isSelected());
                    testConnectionResultLabel.setForeground(new JBColor(new Color(200, 0, 0), new Color(255, 100, 100)));
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                    testConnectionResultLabel.setText("✗ 연결 실패: " + msg);
                });
                return null;
            });
    }

    private static String normalizeExceptionKeys(String text) {
        LocaleGridSettingsState state = new LocaleGridSettingsState();
        state.setExceptionKeysFromCsv(text);
        return String.join(",", state.getExceptionKeyList());
    }

    private String selectedScriptSeverity() {
        return SCRIPT_ERROR_LABEL.equals(localeScriptSeverityComboBox.getSelectedItem()) ? "ERROR" : "WARNING";
    }

    private static String normalizeScriptSeverity(String severity) {
        return "ERROR".equalsIgnoreCase(severity) ? "ERROR" : "WARNING";
    }

    private void updateScriptSeverityEnabled() {
        if (localeScriptSeverityComboBox != null && localeScriptValidationCheckBox != null) {
            localeScriptSeverityComboBox.setEnabled(localeScriptValidationCheckBox.isSelected());
        }
    }

    private boolean hasModifiedLocaleGridEditor() {
        for (FileEditor editor : FileEditorManager.getInstance(project).getAllEditors()) {
            if (editor instanceof LocaleGridFileEditor localeGridEditor && localeGridEditor.isModified()) {
                return true;
            }
        }
        return false;
    }

    private record StructuralSettingsSnapshot(
        String localesRoot,
        List<String> manualLocales,
        List<String> exceptionKeys
    ) {
        private static StructuralSettingsSnapshot capture(LocaleGridSettingsState settings) {
            String root = settings.localesRoot == null || settings.localesRoot.isBlank()
                ? "locales"
                : settings.localesRoot.trim();
            return new StructuralSettingsSnapshot(
                root,
                List.copyOf(settings.getManualLocaleList()),
                List.copyOf(settings.getExceptionKeyList())
            );
        }
    }
}
