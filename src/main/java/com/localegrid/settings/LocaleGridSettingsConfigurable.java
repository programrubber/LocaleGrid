package com.localegrid.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HideableTitledPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class LocaleGridSettingsConfigurable implements Configurable {
    private final LocaleGridSettingsState state;
    private JTextField localesRootField;
    private com.intellij.ui.components.JBTextField manualLocalesField;
    private JTextField exceptionKeysField;
    private JComboBox<Integer> indentComboBox;

    public LocaleGridSettingsConfigurable(Project project) {
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
        // 콤보박스가 가로로 꽉 차서 어색하지 않도록 왼쪽 정렬된 래퍼 패널 이용
        JPanel indentComboPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        indentComboPanel.setOpaque(false);
        indentComboPanel.add(indentComboBox);
        JComponent indentWrapper = createFieldWithHint(indentComboPanel,
            "적용 시 저장되는 JSON 들여쓰기 칸 수입니다. 기본값은 2입니다.");

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

        // 6. 고급 설정 접이식 패널 (HideableTitledPanel)
        HideableTitledPanel advancedPanel = new HideableTitledPanel("고급 설정", advancedContent, true);
        
        c.gridy = 5;
        c.gridx = 0;
        c.gridwidth = 2;
        c.insets = new Insets(0, 0, 8, 0);
        panel.add(advancedPanel, c);

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
        return !localesRootField.getText().equals(state.localesRoot)
            || !manualLocalesField.getText().equals(state.manualLocales)
            || !normalizeExceptionKeys(exceptionKeysField.getText()).equals(String.join(",", state.getExceptionKeyList()))
            || (selectedIndent != null && selectedIndent != state.jsonIndent);
    }

    @Override
    public void apply() {
        state.localesRoot = localesRootField.getText().trim().isEmpty() ? "locales" : localesRootField.getText().trim();
        state.manualLocales = manualLocalesField.getText().trim();
        state.setExceptionKeysFromCsv(exceptionKeysField.getText());
        Integer selectedIndent = (Integer) indentComboBox.getSelectedItem();
        if (selectedIndent != null) {
            state.jsonIndent = selectedIndent;
        }
    }

    @Override
    public void reset() {
        localesRootField.setText(state.localesRoot);
        manualLocalesField.setText(state.manualLocales);
        exceptionKeysField.setText(String.join(", ", state.getExceptionKeyList()));
        indentComboBox.setSelectedItem(state.jsonIndent);
    }

    private static String normalizeExceptionKeys(String text) {
        LocaleGridSettingsState state = new LocaleGridSettingsState();
        state.setExceptionKeysFromCsv(text);
        return String.join(",", state.getExceptionKeyList());
    }
}
