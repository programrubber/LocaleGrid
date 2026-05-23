package com.localegrid.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.localegrid.core.DotPath;
import com.localegrid.core.SaveResult;
import com.localegrid.core.TableValidator;
import com.localegrid.core.TranslationTableLoader;
import com.localegrid.core.TranslationTableSaver;
import com.localegrid.model.Diagnostic;
import com.localegrid.model.LocaleGridRow;
import com.localegrid.model.LocaleValue;
import com.localegrid.model.TranslationTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class LocaleGridFileEditor extends UserDataHolderBase implements FileEditor {
    private final Project project;
    private final VirtualFile file;
    private final JPanel root = new JPanel(new BorderLayout());
    private final JPanel columnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    private final JPanel detailFields = new JPanel(new GridBagLayout());
    private final JLabel detailTitle = new JLabel("Select a row to edit translations.");
    private final LocaleGridTableModel model = new LocaleGridTableModel();
    private final JBTable grid = new JBTable(model);
    private final JTextField searchField = new JTextField();
    private final JCheckBox missingOnly = new JCheckBox("Missing");
    private final JCheckBox modifiedOnly = new JCheckBox("Modified");
    private final JCheckBox deletedOnly = new JCheckBox("Deleted");
    private final JCheckBox issueOnly = new JCheckBox("Issue");
    private final JCheckBox bundleColumnCheck = new JCheckBox(LocaleGridTableModel.BUNDLE_COLUMN_NAME);
    private final Map<String, JCheckBox> localeColumnChecks = new LinkedHashMap<>();
    private final JLabel statusLabel = new JLabel(" ");
    private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
    private TranslationTable translationTable;
    private boolean updatingDetail;

    public LocaleGridFileEditor(Project project, VirtualFile file) {
        this.project = project;
        this.file = file;
        buildUi();
        reload();
    }

    private void buildUi() {
        JPanel top = new JPanel(new BorderLayout());
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton addButton = new JButton("Add Row");
        JButton renameButton = new JButton("Rename Key");
        JButton deleteButton = new JButton("Delete Row");
        JButton saveButton = new JButton("Save");
        JButton refreshButton = new JButton("Refresh");
        JButton validateButton = new JButton("Validate");

        searchField.putClientProperty("JTextField.placeholderText", "Search key or value");
        searchField.setPreferredSize(new Dimension(220, 30));

        toolbar.add(addButton);
        toolbar.add(renameButton);
        toolbar.add(deleteButton);
        toolbar.add(saveButton);
        toolbar.add(refreshButton);
        toolbar.add(validateButton);
        toolbar.add(new JLabel("Search"));
        toolbar.add(searchField);
        toolbar.add(missingOnly);
        toolbar.add(modifiedOnly);
        toolbar.add(deletedOnly);
        toolbar.add(issueOnly);

        top.add(toolbar, BorderLayout.NORTH);
        top.add(columnPanel, BorderLayout.SOUTH);

        grid.setStriped(true);
        grid.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        grid.setDefaultRenderer(Object.class, new LocaleGridCellRenderer());
        grid.getTableHeader().setReorderingAllowed(false);
        grid.setRowHeight(30);
        grid.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        grid.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDetailPanel(selectedRow());
            }
        });

        JPanel detailPanel = new JPanel(new BorderLayout(0, 6));
        detailPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        detailTitle.setFont(detailTitle.getFont().deriveFont(Font.BOLD));
        detailPanel.add(detailTitle, BorderLayout.NORTH);
        detailPanel.add(new JBScrollPane(detailFields), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JBScrollPane(grid), detailPanel);
        splitPane.setResizeWeight(0.68);
        splitPane.setBorder(null);

        root.add(top, BorderLayout.NORTH);
        root.add(splitPane, BorderLayout.CENTER);
        root.add(statusLabel, BorderLayout.SOUTH);

        addButton.addActionListener(e -> addRow());
        renameButton.addActionListener(e -> renameSelectedRow());
        deleteButton.addActionListener(e -> deleteSelectedRow());
        saveButton.addActionListener(e -> save());
        refreshButton.addActionListener(e -> reload());
        validateButton.addActionListener(e -> validateCurrentTable());

        DocumentListener filterListener = new SimpleDocumentListener(this::applyFilter);
        searchField.getDocument().addDocumentListener(filterListener);
        missingOnly.addActionListener(e -> applyFilter());
        modifiedOnly.addActionListener(e -> applyFilter());
        deletedOnly.addActionListener(e -> applyFilter());
        issueOnly.addActionListener(e -> applyFilter());
        bundleColumnCheck.addActionListener(e -> applyColumnVisibility());
    }

    private void reload() {
        try {
            translationTable = new TranslationTableLoader().load(project, file);
            model.setTable(translationTable);
            rebuildColumnControls();
            applyFilter();
            installTableRenderer();
            resizeColumns();
            updateDetailPanel(null);
            updateStatus();
        } catch (RuntimeException ex) {
            statusLabel.setText("Load failed: " + ex.getMessage());
        }
    }

    private void rebuildColumnControls() {
        columnPanel.removeAll();
        localeColumnChecks.clear();
        columnPanel.add(new JLabel("Columns"));
        if (translationTable != null) {
            for (String locale : translationTable.getLocales()) {
                JCheckBox checkBox = new JCheckBox(locale, true);
                checkBox.addActionListener(e -> applyColumnVisibility());
                localeColumnChecks.put(locale, checkBox);
                columnPanel.add(checkBox);
            }
        }
        bundleColumnCheck.setSelected(false);
        columnPanel.add(bundleColumnCheck);
        columnPanel.revalidate();
        columnPanel.repaint();
    }

    private void applyColumnVisibility() {
        Set<String> visibleLocales = new HashSet<>();
        for (Map.Entry<String, JCheckBox> entry : localeColumnChecks.entrySet()) {
            if (entry.getValue().isSelected()) {
                visibleLocales.add(entry.getKey());
            }
        }
        model.setVisibleLocales(visibleLocales);
        model.setBundleVisible(bundleColumnCheck.isSelected());
        installTableRenderer();
        resizeColumns();
        grid.setRowHeight(model.isBundleVisible() ? 92 : 30);
    }

    private void installTableRenderer() {
        grid.setDefaultRenderer(Object.class, new LocaleGridCellRenderer());
    }

    private void applyFilter() {
        model.applyFilter(
            searchField.getText(),
            missingOnly.isSelected(),
            modifiedOnly.isSelected(),
            deletedOnly.isSelected(),
            issueOnly.isSelected()
        );
    }

    private void addRow() {
        String key = Messages.showInputDialog(project, "New dot path key", "Add Row", Messages.getQuestionIcon());
        if (key == null) {
            return;
        }
        key = key.trim();
        String error = validateNewKey(key, null);
        if (error != null) {
            Messages.showErrorDialog(project, error, "Add Row");
            return;
        }

        LocaleGridRow row = new LocaleGridRow(key, false);
        for (String locale : translationTable.getLocales()) {
            row.putValue(locale, LocaleValue.missing());
        }
        int insertAt = translationTable.getRows().size();
        LocaleGridRow selected = selectedRow();
        if (selected != null) {
            insertAt = translationTable.getRows().indexOf(selected) + 1;
        }
        translationTable.getRows().add(insertAt, row);
        validateCurrentTable();
        selectRow(row);
    }

    private void renameSelectedRow() {
        LocaleGridRow row = selectedRow();
        if (row == null) {
            return;
        }
        if (row.isComment()) {
            Messages.showWarningDialog(project, "Comment rows are readonly.", "Rename Key");
            return;
        }
        String nextKey = Messages.showInputDialog(project, "New dot path key", "Rename Key", Messages.getQuestionIcon(), row.getKey(), null);
        if (nextKey == null) {
            return;
        }
        nextKey = nextKey.trim();
        String error = validateNewKey(nextKey, row);
        if (error != null) {
            Messages.showErrorDialog(project, error, "Rename Key");
            return;
        }
        row.rename(nextKey);
        validateCurrentTable();
        selectRow(row);
    }

    private void deleteSelectedRow() {
        LocaleGridRow row = selectedRow();
        if (row == null) {
            return;
        }
        row.setDeleted(true);
        validateCurrentTable();
        selectRow(row);
    }

    private void save() {
        validateCurrentTable();
        if (translationTable.hasErrors()) {
            Messages.showErrorDialog(project, "Cannot save while validation errors remain.", "LocaleGrid Save");
            return;
        }

        boolean createMissingFiles = false;
        if (hasMissingFilesWithValues()) {
            int answer = Messages.showYesNoDialog(
                project,
                "Some locale files are missing. Create them during save?",
                "LocaleGrid Save",
                Messages.getQuestionIcon()
            );
            createMissingFiles = answer == Messages.YES;
        }

        SaveResult result = new TranslationTableSaver().save(project, translationTable, createMissingFiles);
        if (result.hasErrors()) {
            Messages.showErrorDialog(project, summarizeDiagnostics(result), "LocaleGrid Save");
            return;
        }

        Messages.showInfoMessage(project, summarizeSave(result), "LocaleGrid Save");
        reload();
    }

    private boolean hasMissingFilesWithValues() {
        for (String locale : translationTable.getLocales()) {
            File target = translationTable.getFilesByLocale().get(locale);
            if (target != null && !target.exists()) {
                for (LocaleGridRow row : translationTable.getRows()) {
                    if (!row.isDeleted() && !row.getValue(locale).getDisplayText().isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void validateCurrentTable() {
        translationTable.getDiagnostics().clear();
        TableValidator.validate(translationTable);
        applyFilter();
        updateDetailPanel(selectedRow());
        updateStatus();
    }

    private String validateNewKey(String key, LocaleGridRow currentRow) {
        String error = DotPath.validate(key);
        if (error != null) {
            return error;
        }
        Set<String> keys = new HashSet<>();
        for (LocaleGridRow row : translationTable.getRows()) {
            if (row == currentRow || row.isDeleted()) {
                continue;
            }
            keys.add(row.getKey());
        }
        if (keys.contains(key)) {
            return "Key already exists.";
        }
        if (DotPath.conflictsWithAny(key, keys)) {
            return "Key conflicts with an existing dot path.";
        }
        return null;
    }

    private LocaleGridRow selectedRow() {
        int selected = grid.getSelectedRow();
        if (selected < 0) {
            return null;
        }
        return model.getRow(selected);
    }

    private void selectRow(LocaleGridRow row) {
        int index = model.indexOf(row);
        if (index >= 0) {
            grid.getSelectionModel().setSelectionInterval(index, index);
            grid.scrollRectToVisible(grid.getCellRect(index, 0, true));
        }
    }

    private void updateDetailPanel(LocaleGridRow row) {
        updatingDetail = true;
        detailFields.removeAll();
        if (row == null || translationTable == null) {
            detailTitle.setText("Select a row to edit translations.");
            detailFields.revalidate();
            detailFields.repaint();
            updatingDetail = false;
            return;
        }

        detailTitle.setText(row.getKey());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int y = 0;
        for (String locale : translationTable.getLocales()) {
            LocaleValue value = row.getValue(locale);
            JLabel localeLabel = new JLabel(locale);
            localeLabel.setFont(localeLabel.getFont().deriveFont(Font.BOLD));
            JTextArea editor = new JTextArea(value.getDisplayText(), 2, 48);
            editor.setLineWrap(true);
            editor.setWrapStyleWord(true);
            editor.setEditable(!row.isDeleted() && !row.isComment() && value.isEditable());
            editor.setEnabled(editor.isEditable());
            editor.setToolTipText(value.isEditable() ? null : "Readonly value type");
            editor.getDocument().addDocumentListener(new SimpleDocumentListener(() -> {
                if (updatingDetail) {
                    return;
                }
                value.setText(editor.getText());
                model.refreshRow(row);
                updateStatus();
            }));

            c.gridx = 0;
            c.gridy = y;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            detailFields.add(localeLabel, c);

            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            detailFields.add(new JBScrollPane(editor), c);
            y++;
        }

        c.gridx = 0;
        c.gridy = y;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        detailFields.add(Box.createVerticalGlue(), c);
        detailFields.revalidate();
        detailFields.repaint();
        updatingDetail = false;
    }

    private void updateStatus() {
        long errors = translationTable.getDiagnostics().stream().filter(d -> d.getSeverity() == Diagnostic.Severity.ERROR).count();
        long warnings = translationTable.getDiagnostics().stream().filter(d -> d.getSeverity() == Diagnostic.Severity.WARNING).count();
        statusLabel.setText(
            "Category: " + translationTable.getCategory()
                + " | Locales: " + String.join(", ", translationTable.getLocales())
                + " | Rows: " + translationTable.getRows().size()
                + " | Errors: " + errors
                + " | Warnings: " + warnings
        );
    }

    private void resizeColumns() {
        if (grid.getColumnModel().getColumnCount() == 0) {
            return;
        }
        grid.getColumnModel().getColumn(0).setPreferredWidth(280);
        for (int i = 1; i < grid.getColumnModel().getColumnCount(); i++) {
            String columnName = grid.getColumnName(i);
            grid.getColumnModel().getColumn(i).setPreferredWidth(
                LocaleGridTableModel.BUNDLE_COLUMN_NAME.equals(columnName) ? 360 : 220
            );
        }
    }

    private static String summarizeDiagnostics(SaveResult result) {
        StringBuilder out = new StringBuilder();
        for (Diagnostic diagnostic : result.getDiagnostics()) {
            if (diagnostic.getSeverity() == Diagnostic.Severity.ERROR) {
                out.append(diagnostic.getMessage()).append('\n');
            }
        }
        return out.length() == 0 ? "Save failed." : out.toString();
    }

    private static String summarizeSave(SaveResult result) {
        return "Saved files: " + result.getWrittenFiles().size()
            + "\nCreated files: " + result.getCreatedFiles().size()
            + "\nAdded keys: " + result.getAddedKeys()
            + "\nModified keys: " + result.getModifiedKeys()
            + "\nDeleted keys: " + result.getDeletedKeys();
    }

    @Override
    public @NotNull JComponent getComponent() {
        return root;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return grid;
    }

    @Override
    public @NotNull String getName() {
        return "Locale Grid";
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
    }

    @Override
    public boolean isModified() {
        return translationTable != null && translationTable.getRows().stream().anyMatch(LocaleGridRow::isModified);
    }

    @Override
    public boolean isValid() {
        return file.isValid();
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener(listener);
    }

    @Override
    public void dispose() {
    }

    private static final class SimpleDocumentListener implements DocumentListener {
        private final Runnable callback;

        private SimpleDocumentListener(Runnable callback) {
            this.callback = callback;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            callback.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            callback.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            callback.run();
        }
    }
}
