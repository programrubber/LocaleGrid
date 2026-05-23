package com.localegrid.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class LocaleGridSettingsConfigurable implements Configurable {
    private final LocaleGridSettingsState state;
    private JTextField localesRootField;
    private JTextField manualLocalesField;
    private JTextField commentKeysField;
    private JSpinner indentSpinner;

    public LocaleGridSettingsConfigurable(Project project) {
        this.state = LocaleGridSettingsState.getInstance(project);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "LocaleGrid";
    }

    @Override
    public @Nullable JComponent createComponent() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        localesRootField = new JTextField(state.localesRoot, 32);
        manualLocalesField = new JTextField(state.manualLocales, 32);
        commentKeysField = new JTextField(state.commentKeys, 32);
        indentSpinner = new JSpinner(new SpinnerNumberModel(state.jsonIndent, 2, 8, 1));

        addRow(panel, c, 0, "Locales root", localesRootField);
        addRow(panel, c, 1, "Manual locales", manualLocalesField);
        addRow(panel, c, 2, "Comment keys", commentKeysField);
        addRow(panel, c, 3, "JSON indent", indentSpinner);
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
        return !localesRootField.getText().equals(state.localesRoot)
            || !manualLocalesField.getText().equals(state.manualLocales)
            || !commentKeysField.getText().equals(state.commentKeys)
            || ((Integer) indentSpinner.getValue()) != state.jsonIndent;
    }

    @Override
    public void apply() {
        state.localesRoot = localesRootField.getText().trim().isEmpty() ? "locales" : localesRootField.getText().trim();
        state.manualLocales = manualLocalesField.getText().trim();
        state.commentKeys = commentKeysField.getText().trim();
        state.jsonIndent = (Integer) indentSpinner.getValue();
    }

    @Override
    public void reset() {
        localesRootField.setText(state.localesRoot);
        manualLocalesField.setText(state.manualLocales);
        commentKeysField.setText(state.commentKeys);
        indentSpinner.setValue(state.jsonIndent);
    }
}
