// 설정 창 구현: SecuiSettingsConfigurable 클래스는 설정 창의 UI와 로직을 정의

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class SecuiSettingsConfigurable implements Configurable {
    private JTextField nexusUrlField;
    private JCheckBox notificationCheckBox; // 체크박스 추가
    private final SecuiSettingsState settingsState;
    private final Project project;

    public SecuiSettingsConfigurable(Project project) {
        this.project = project;
        this.settingsState = SecuiSettingsState.getInstance(project);
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Secui Version Checker";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        JPanel panel = new JPanel(new BorderLayout());

        // 체크박스 부분
        notificationCheckBox = new JCheckBox("프로젝트 시작시 알림 여부", settingsState.getNotificationEnabled());
        JPanel notificationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        notificationPanel.add(notificationCheckBox);

        // URL 입력 부분
        JLabel label = new JLabel("Nexus Server URL:");
        nexusUrlField = new JTextField(settingsState.getNexusUrl(), 30);
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        inputPanel.add(label);
        inputPanel.add(nexusUrlField);

        // 체크박스를 최상단에, URL 입력 부분을 그 아래에 추가
        panel.add(notificationPanel, BorderLayout.NORTH);
        panel.add(inputPanel, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public boolean isModified() {
        return !nexusUrlField.getText().equals(settingsState.getNexusUrl()) ||
                notificationCheckBox.isSelected() != settingsState.getNotificationEnabled();
    }

    @Override
    public void apply() {
        settingsState.setNexusUrl(nexusUrlField.getText());
        settingsState.setNotificationEnabled(notificationCheckBox.isSelected());
    }

    @Override
    public void reset() {
        nexusUrlField.setText(settingsState.getNexusUrl());
        notificationCheckBox.setSelected(settingsState.getNotificationEnabled());
    }

    @Override
    public void disposeUIResources() {
        nexusUrlField = null;
        notificationCheckBox = null;
    }
}