package com.localegrid.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
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
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
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
    private final JPanel detailFields = new JPanel();
    private final JLabel detailTitle = new JLabel("편집할 행을 선택하세요.");
    private final LocaleGridTableModel model = new LocaleGridTableModel();
    private final JBTable grid = new JBTable(model) {
        @Override
        public String getToolTipText(MouseEvent event) {
            int viewRow = rowAtPoint(event.getPoint());
            int viewColumn = columnAtPoint(event.getPoint());
            if (viewRow >= 0 && viewColumn >= 0 && convertColumnIndexToModel(viewColumn) == 0) {
                Object value = getValueAt(viewRow, viewColumn);
                String code = value == null ? "" : String.valueOf(value);
                Rectangle cell = getCellRect(viewRow, viewColumn, false);
                if (LocaleGridStatusRenderer.containsBadgePoint(
                    code,
                    event.getX() - cell.x,
                    event.getY() - cell.y,
                    cell.width,
                    cell.height
                )) {
                    return LocaleGridStatusRenderer.tooltipText(code);
                }
                return null;
            }
            return super.getToolTipText(event);
        }
    };
    private final JTextField searchField = new JTextField();
    private final JCheckBox missingOnly = new JCheckBox("빈 값");
    private final JCheckBox modifiedOnly = new JCheckBox("수정됨");
    private final JCheckBox deletedOnly = new JCheckBox("삭제 후보");
    private final JCheckBox issueOnly = new JCheckBox("문제 있음");
    private final JCheckBox bundleColumnCheck = new JCheckBox(LocaleGridTableModel.BUNDLE_COLUMN_NAME);
    private final Map<String, JCheckBox> localeColumnChecks = new LinkedHashMap<>();
    private final JLabel statusLabel = new JLabel(" ");
    private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
    private TranslationTable translationTable;
    private boolean updatingDetail;
    private boolean updatingColumnControls;
    private boolean skipNextSelectReload;
    private boolean suppressUnsavedPrompt;
    private boolean lastModifiedState;

    public LocaleGridFileEditor(Project project, VirtualFile file) {
        this.project = project;
        this.file = file;
        installClosePrompt();
        buildUi();
        reload();
    }

    private void installClosePrompt() {
        project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, new FileEditorManagerListener.Before() {
            @Override
            public void beforeFileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile closingFile) {
                if (!file.equals(closingFile) || !isModified() || suppressUnsavedPrompt) {
                    return;
                }

                suppressUnsavedPrompt = true;
                int answer = Messages.showYesNoDialog(
                    project,
                    "저장되지 않은 다국어 에디터 변경 사항이 있습니다.\n저장하지 않고 닫을까요?",
                    "LocaleGrid",
                    "닫기",
                    "계속 편집",
                    Messages.getWarningIcon()
                );
                if (answer == Messages.YES) {
                    discardUnsavedGridChanges();
                    suppressUnsavedPrompt = false;
                    return;
                }

                skipNextSelectReload = true;
                SwingUtilities.invokeLater(() -> {
                    source.openFile(file, true);
                    source.setSelectedEditor(file, "locale-grid");
                    suppressUnsavedPrompt = false;
                });
            }
        });
    }

    private void buildUi() {
        JPanel top = new JPanel(new BorderLayout());
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton addButton = new JButton("행 추가");
        JButton renameButton = new JButton("키 이름 변경");
        JButton deleteButton = new JButton("행 삭제");
        JButton saveButton = new JButton("저장");
        JButton refreshButton = new JButton("새로고침");
        JButton validateButton = new JButton("검증");

        searchField.putClientProperty("JTextField.placeholderText", "key 또는 value 검색");
        searchField.setPreferredSize(new Dimension(220, 30));

        toolbar.add(addButton);
        toolbar.add(renameButton);
        toolbar.add(deleteButton);
        toolbar.add(saveButton);
        toolbar.add(refreshButton);
        toolbar.add(validateButton);
        toolbar.add(new JLabel("검색"));
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
        grid.setToolTipText("");
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
        detailFields.setLayout(new BoxLayout(detailFields, BoxLayout.Y_AXIS));
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
        reload(null);
    }

    private void reload(@Nullable ViewState state) {
        try {
            translationTable = new TranslationTableLoader().load(project, file);
            model.setTable(translationTable);
            rebuildColumnControls();
            restoreColumnControls(state);
            applyFilter();
            installTableRenderer();
            resizeColumns();
            restoreSelection(state);
            updateStatus();
            updateModifiedState();
        } catch (RuntimeException ex) {
            statusLabel.setText("로드 실패: " + ex.getMessage());
        }
    }

    private void rebuildColumnControls() {
        columnPanel.removeAll();
        localeColumnChecks.clear();
        columnPanel.add(new JLabel("컬럼"));
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
        if (updatingColumnControls) {
            return;
        }
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

    private void restoreColumnControls(@Nullable ViewState state) {
        if (state == null) {
            applyColumnVisibility();
            return;
        }

        updatingColumnControls = true;
        try {
            for (Map.Entry<String, JCheckBox> entry : localeColumnChecks.entrySet()) {
                entry.getValue().setSelected(state.visibleLocales.contains(entry.getKey()));
            }
            bundleColumnCheck.setSelected(state.bundleVisible);
        } finally {
            updatingColumnControls = false;
        }
        applyColumnVisibility();
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

    private ViewState captureViewState() {
        Set<String> visibleLocales = new HashSet<>();
        for (Map.Entry<String, JCheckBox> entry : localeColumnChecks.entrySet()) {
            if (entry.getValue().isSelected()) {
                visibleLocales.add(entry.getKey());
            }
        }
        LocaleGridRow row = selectedRow();
        return new ViewState(visibleLocales, bundleColumnCheck.isSelected(), row == null ? null : row.getKey());
    }

    private void restoreSelection(@Nullable ViewState state) {
        if (state == null || state.selectedKey == null) {
            updateDetailPanel(null);
            return;
        }
        for (int i = 0; i < model.getRowCount(); i++) {
            LocaleGridRow row = model.getRow(i);
            if (state.selectedKey.equals(row.getKey())) {
                selectRow(row);
                updateDetailPanel(row);
                return;
            }
        }
        updateDetailPanel(null);
    }

    private void addRow() {
        String key = Messages.showInputDialog(project, "새 dot path key", "행 추가", Messages.getQuestionIcon());
        if (key == null) {
            return;
        }
        key = key.trim();
        String error = validateNewKey(key, null);
        if (error != null) {
            Messages.showErrorDialog(project, error, "행 추가");
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
        updateModifiedState();
    }

    private void renameSelectedRow() {
        LocaleGridRow row = selectedRow();
        if (row == null) {
            return;
        }
        if (row.isComment()) {
            Messages.showWarningDialog(project, "구역 표시 행은 수정할 수 없습니다.", "키 이름 변경");
            return;
        }
        String nextKey = Messages.showInputDialog(project, "새 dot path key", "키 이름 변경", Messages.getQuestionIcon(), row.getKey(), null);
        if (nextKey == null) {
            return;
        }
        nextKey = nextKey.trim();
        String error = validateNewKey(nextKey, row);
        if (error != null) {
            Messages.showErrorDialog(project, error, "키 이름 변경");
            return;
        }
        row.rename(nextKey);
        validateCurrentTable();
        selectRow(row);
        updateModifiedState();
    }

    private void deleteSelectedRow() {
        LocaleGridRow row = selectedRow();
        if (row == null) {
            return;
        }
        row.setDeleted(true);
        validateCurrentTable();
        selectRow(row);
        updateModifiedState();
    }

    private void save() {
        validateCurrentTable();
        if (translationTable.hasErrors()) {
            Messages.showErrorDialog(project, "검증 오류가 남아 있어 저장할 수 없습니다.", "LocaleGrid 저장");
            return;
        }

        boolean createMissingFiles = false;
        if (hasMissingFilesWithValues()) {
            int answer = Messages.showYesNoDialog(
                project,
                "누락된 locale 파일이 있습니다. 저장하면서 생성할까요?",
                "LocaleGrid 저장",
                Messages.getQuestionIcon()
            );
            createMissingFiles = answer == Messages.YES;
        }

        SaveResult result = new TranslationTableSaver().save(project, translationTable, createMissingFiles);
        if (result.hasErrors()) {
            Messages.showErrorDialog(project, summarizeDiagnostics(result), "LocaleGrid 저장");
            return;
        }

        Messages.showInfoMessage(project, summarizeSave(result), "LocaleGrid 저장");
        reload(captureViewState());
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
            return "이미 존재하는 key입니다.";
        }
        if (DotPath.conflictsWithAny(key, keys)) {
            return "기존 dot path 구조와 충돌합니다.";
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
            detailTitle.setText("편집할 행을 선택하세요.");
            detailFields.revalidate();
            detailFields.repaint();
            updatingDetail = false;
            return;
        }

        detailTitle.setText(row.getKey());
        for (String locale : translationTable.getLocales()) {
            LocaleValue value = row.getValue(locale);
            JLabel localeLabel = new JLabel(locale);
            localeLabel.setFont(localeLabel.getFont().deriveFont(Font.BOLD));
            localeLabel.setPreferredSize(new Dimension(44, 26));
            localeLabel.setMinimumSize(new Dimension(44, 26));

            JTextArea editor = new JTextArea(value.getDisplayText(), 2, 48);
            editor.setLineWrap(true);
            editor.setWrapStyleWord(true);
            installTextAreaFocusTraversal(editor);
            editor.setEditable(!row.isDeleted() && !row.isComment() && value.isEditable());
            editor.setEnabled(editor.isEditable());
            editor.setToolTipText(value.isEditable() ? null : "읽기 전용 value 타입입니다.");
            editor.getDocument().addDocumentListener(new SimpleDocumentListener(() -> {
                if (updatingDetail) {
                    return;
                }
                value.setText(editor.getText());
                refreshAfterEdit(row);
            }));

            JPanel rowPanel = new JPanel(new BorderLayout(8, 0));
            rowPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
            rowPanel.add(localeLabel, BorderLayout.WEST);
            rowPanel.add(new JBScrollPane(editor), BorderLayout.CENTER);
            rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            detailFields.add(rowPanel);
        }

        detailFields.add(Box.createVerticalGlue());
        detailFields.revalidate();
        detailFields.repaint();
        updatingDetail = false;
    }

    private void refreshAfterEdit(LocaleGridRow row) {
        translationTable.getDiagnostics().clear();
        TableValidator.validate(translationTable);
        model.refreshRow(row);
        updateStatus();
        updateModifiedState();
    }

    private void discardUnsavedGridChanges() {
        reload(captureViewState());
    }

    private void updateModifiedState() {
        boolean nextModifiedState = isModified();
        if (lastModifiedState != nextModifiedState) {
            changeSupport.firePropertyChange(FileEditor.PROP_MODIFIED, lastModifiedState, nextModifiedState);
            lastModifiedState = nextModifiedState;
        }
    }

    private void updateStatus() {
        long errors = translationTable.getDiagnostics().stream().filter(d -> d.getSeverity() == Diagnostic.Severity.ERROR).count();
        long warnings = translationTable.getDiagnostics().stream().filter(d -> d.getSeverity() == Diagnostic.Severity.WARNING).count();
        statusLabel.setText(
            "카테고리: " + translationTable.getCategory()
                + " | locale: " + String.join(", ", translationTable.getLocales())
                + " | 행: " + translationTable.getRows().size()
                + " | 오류: " + errors
                + " | 경고: " + warnings
        );
    }

    private void resizeColumns() {
        if (grid.getColumnModel().getColumnCount() == 0) {
            return;
        }
        grid.getColumnModel().getColumn(0).setCellRenderer(new LocaleGridStatusRenderer());
        grid.getColumnModel().getColumn(0).setPreferredWidth(64);
        grid.getColumnModel().getColumn(0).setMaxWidth(72);
        grid.getColumnModel().getColumn(1).setPreferredWidth(280);
        for (int i = 1; i < grid.getColumnModel().getColumnCount(); i++) {
            if (i == 1) {
                continue;
            }
            String columnName = grid.getColumnName(i);
            grid.getColumnModel().getColumn(i).setPreferredWidth(
                LocaleGridTableModel.BUNDLE_COLUMN_NAME.equals(columnName) ? 360 : 220
            );
        }
    }

    private static void installTextAreaFocusTraversal(JTextArea editor) {
        editor.setFocusTraversalKeysEnabled(true);
        InputMap inputMap = editor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = editor.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("TAB"), "localeGridTransferFocus");
        inputMap.put(KeyStroke.getKeyStroke("shift TAB"), "localeGridTransferFocusBackward");
        actionMap.put("localeGridTransferFocus", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ((Component) e.getSource()).transferFocus();
            }
        });
        actionMap.put("localeGridTransferFocusBackward", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ((Component) e.getSource()).transferFocusBackward();
            }
        });
    }

    private static String summarizeDiagnostics(SaveResult result) {
        StringBuilder out = new StringBuilder();
        for (Diagnostic diagnostic : result.getDiagnostics()) {
            if (diagnostic.getSeverity() == Diagnostic.Severity.ERROR) {
                out.append(diagnostic.getMessage()).append('\n');
            }
        }
        return out.length() == 0 ? "저장에 실패했습니다." : out.toString();
    }

    private static String summarizeSave(SaveResult result) {
        return "저장된 파일: " + result.getWrittenFiles().size()
            + "\n생성된 파일: " + result.getCreatedFiles().size()
            + "\n추가된 key: " + result.getAddedKeys()
            + "\n수정된 key: " + result.getModifiedKeys()
            + "\n삭제된 key: " + result.getDeletedKeys();
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
    public @NotNull VirtualFile getFile() {
        return file;
    }

    @Override
    public @NotNull String getName() {
        return "다국어 에디터";
    }

    @Override
    public void selectNotify() {
        if (skipNextSelectReload) {
            skipNextSelectReload = false;
            return;
        }
        reload(captureViewState());
    }

    @Override
    public void deselectNotify() {
        if (!isModified() || suppressUnsavedPrompt) {
            return;
        }

        int answer = Messages.showYesNoDialog(
            project,
            "저장되지 않은 다국어 에디터 변경 사항이 있습니다.\n이동하면 변경 사항을 버리고 JSON 내용을 다시 읽습니다.\n계속 이동할까요?",
            "LocaleGrid",
            "이동",
            "취소",
            Messages.getWarningIcon()
        );
        if (answer == Messages.YES) {
            discardUnsavedGridChanges();
            return;
        }

        skipNextSelectReload = true;
        SwingUtilities.invokeLater(() -> FileEditorManager.getInstance(project).setSelectedEditor(file, "locale-grid"));
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

    private static final class ViewState {
        private final Set<String> visibleLocales;
        private final boolean bundleVisible;
        private final String selectedKey;

        private ViewState(Set<String> visibleLocales, boolean bundleVisible, @Nullable String selectedKey) {
            this.visibleLocales = visibleLocales;
            this.bundleVisible = bundleVisible;
            this.selectedKey = selectedKey;
        }
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
