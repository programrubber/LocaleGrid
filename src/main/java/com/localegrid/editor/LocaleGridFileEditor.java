package com.localegrid.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
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
    private static final String EDITOR_NAME = "다국어 에디터";
    private static final String EDITING_EDITOR_NAME = "다국어 에디터 (편집중)";

    private final Project project;
    private final VirtualFile file;
    private final JPanel root = new JPanel(new BorderLayout());
    private final JPanel columnPanel = new JPanel(new BorderLayout(8, 0));
    private final JPanel columnChecksPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    private final JPanel rowActionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
    private final JPanel detailFields = new JPanel();
    private final JLabel detailTitle = new JLabel("편집할 Row를 선택하세요.");
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
    private final StatusFilterButton addedOnly = new StatusFilterButton("추가");
    private final StatusFilterButton warningOnly = new StatusFilterButton("경고");
    private final StatusFilterButton modifiedOnly = new StatusFilterButton("편집");
    private final StatusFilterButton deletedOnly = new StatusFilterButton("삭제");
    private final StatusFilterButton errorOnly = new StatusFilterButton("에러");
    private final JCheckBox bundleColumnCheck = new JCheckBox(LocaleGridTableModel.BUNDLE_COLUMN_NAME);
    private final JButton renameButton = new JButton("편집");
    private final JButton deleteButton = new JButton("삭제");
    private final JButton undoDeleteButton = new JButton("삭제 취소");
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
        JButton addButton = new JButton("추가");
        JButton applyButton = new BottomActionButton(
            "적용",
            new Color(42, 121, 82),
            new Color(53, 145, 99),
            new Color(35, 101, 69),
            new Color(78, 172, 124)
        );
        JButton cancelButton = new BottomActionButton(
            "취소",
            new Color(132, 58, 62),
            new Color(154, 68, 73),
            new Color(108, 48, 52),
            new Color(182, 91, 97)
        );

        searchField.putClientProperty("JTextField.placeholderText", "key 또는 value 검색");
        searchField.setPreferredSize(new Dimension(220, 30));

        toolbar.add(new JLabel("검색"));
        toolbar.add(searchField);
        toolbar.add(new JLabel("| 필터 :"));
        toolbar.add(addedOnly);
        toolbar.add(warningOnly);
        toolbar.add(modifiedOnly);
        toolbar.add(deletedOnly);
        toolbar.add(errorOnly);

        rowActionsPanel.add(addButton);
        rowActionsPanel.add(renameButton);
        rowActionsPanel.add(deleteButton);
        rowActionsPanel.add(undoDeleteButton);
        columnPanel.add(columnChecksPanel, BorderLayout.CENTER);
        columnPanel.add(rowActionsPanel, BorderLayout.EAST);

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
                LocaleGridRow row = selectedRow();
                updateDetailPanel(row);
                updateRowActionButtons(row);
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

        JPanel bottomActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottomActions.add(cancelButton);
        bottomActions.add(applyButton);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(bottomActions, BorderLayout.EAST);

        root.add(top, BorderLayout.NORTH);
        root.add(splitPane, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        addButton.addActionListener(e -> addRow());
        renameButton.addActionListener(e -> renameSelectedRow());
        deleteButton.addActionListener(e -> deleteSelectedRow());
        undoDeleteButton.addActionListener(e -> undoDeleteSelectedRow());
        applyButton.addActionListener(e -> applyChanges());
        cancelButton.addActionListener(e -> cancelChangesWithConfirm());

        DocumentListener filterListener = new SimpleDocumentListener(this::applyFilter);
        searchField.getDocument().addDocumentListener(filterListener);
        addedOnly.addActionListener(e -> applyFilter());
        warningOnly.addActionListener(e -> applyFilter());
        modifiedOnly.addActionListener(e -> applyFilter());
        deletedOnly.addActionListener(e -> applyFilter());
        errorOnly.addActionListener(e -> applyFilter());
        bundleColumnCheck.addActionListener(e -> applyColumnVisibility());
        updateRowActionButtons(null);
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

    private void cancelChangesWithConfirm() {
        if (isModified()) {
            boolean confirmed = WideConfirmDialog.show(
                project,
                "변경 취소",
                "저장되지 않은 다국어 에디터 변경 사항이 있습니다.\n"
                    + "취소하면 원본 JSON을 다시 불러오고 현재 변경 사항은 사라집니다.\n"
                    + "변경 사항을 취소할까요?",
                "변경 취소",
                "계속 편집",
                Messages.getWarningIcon()
            );
            if (!confirmed) {
                return;
            }
        }
        reload(captureViewState());
    }

    private void rebuildColumnControls() {
        columnChecksPanel.removeAll();
        localeColumnChecks.clear();
        columnChecksPanel.add(new JLabel("Column"));
        if (translationTable != null) {
            for (String locale : translationTable.getLocales()) {
                JCheckBox checkBox = new JCheckBox(locale, true);
                checkBox.addActionListener(e -> applyColumnVisibility());
                localeColumnChecks.put(locale, checkBox);
                columnChecksPanel.add(checkBox);
            }
        }
        bundleColumnCheck.setSelected(false);
        columnChecksPanel.add(bundleColumnCheck);
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
            addedOnly.isSelected(),
            warningOnly.isSelected(),
            modifiedOnly.isSelected(),
            deletedOnly.isSelected(),
            errorOnly.isSelected()
        );
        LocaleGridRow row = selectedRow();
        updateDetailPanel(row);
        updateRowActionButtons(row);
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
            updateRowActionButtons(null);
            return;
        }
        for (int i = 0; i < model.getRowCount(); i++) {
            LocaleGridRow row = model.getRow(i);
            if (state.selectedKey.equals(row.getKey())) {
                selectRow(row);
                updateDetailPanel(row);
                updateRowActionButtons(row);
                return;
            }
        }
        updateDetailPanel(null);
        updateRowActionButtons(null);
    }

    private void addRow() {
        String key = Messages.showInputDialog(project, "key 명 (예: login.title)", "Row 추가", Messages.getQuestionIcon());
        if (key == null) {
            return;
        }
        key = key.trim();
        String error = validateNewKey(key, null);
        if (error != null) {
            Messages.showErrorDialog(project, error, "Row 추가");
            return;
        }

        LocaleGridRow row = new LocaleGridRow(key, false);
        row.markAdded();
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
        if (hasSourceError(row)) {
            Messages.showErrorDialog(
                project,
                "원본 JSON에 같은 key가 중복되어 있어 그리드에서 안전하게 이름을 변경할 수 없습니다.\n"
                    + "JSON 탭에서 중복 key 중 하나를 삭제하거나 이름을 바꾼 뒤 원본을 다시 불러오세요.",
                "편집(key)"
            );
            return;
        }
        if (row.isComment()) {
            Messages.showWarningDialog(
                project,
                "이 Row는 번역 key가 아니라 JSON 주석 marker라 이름 변경 대상이 아닙니다.",
                "편집(key)"
            );
            return;
        }
        String nextKey = Messages.showInputDialog(project, "key 명 (예: login.title)", "편집(key)", Messages.getQuestionIcon(), row.getKey(), null);
        if (nextKey == null) {
            return;
        }
        nextKey = nextKey.trim();
        String error = validateNewKey(nextKey, row);
        if (error != null) {
            Messages.showErrorDialog(project, error, "편집(key)");
            return;
        }
        row.rename(nextKey);
        validateCurrentTable();
        selectRow(row);
        updateModifiedState();
    }

    private boolean hasSourceError(LocaleGridRow row) {
        return translationTable.getSourceDiagnostics().stream()
            .anyMatch(d -> row.getKey().equals(d.getKey()) && d.getSeverity() == Diagnostic.Severity.ERROR);
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

    private void undoDeleteSelectedRow() {
        LocaleGridRow row = selectedRow();
        if (row == null || !row.isDeleted()) {
            return;
        }
        row.setDeleted(false);
        validateCurrentTable();
        selectRow(row);
        updateModifiedState();
    }

    private void applyChanges() {
        validateCurrentTable();
        if (translationTable.hasErrors()) {
            Messages.showErrorDialog(project, "검증 오류가 남아 있어 적용할 수 없습니다.", "LocaleGrid 적용");
            return;
        }

        boolean createMissingFiles = false;
        if (hasMissingFilesWithValues()) {
            int answer = Messages.showYesNoDialog(
                project,
                "누락된 locale 파일이 있습니다. 적용하면서 생성할까요?",
                "LocaleGrid 적용",
                Messages.getQuestionIcon()
            );
            createMissingFiles = answer == Messages.YES;
        }

        SaveResult result = new TranslationTableSaver().save(project, translationTable, createMissingFiles);
        if (result.hasErrors()) {
            Messages.showErrorDialog(project, summarizeDiagnostics(result), "LocaleGrid 적용");
            return;
        }

        Messages.showInfoMessage(project, summarizeSave(result), "LocaleGrid 적용");
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
        translationTable.getDiagnostics().addAll(translationTable.getSourceDiagnostics());
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
        if (selected < 0 || selected >= model.getRowCount()) {
            return null;
        }
        return model.getRow(selected);
    }

    private void updateRowActionButtons(@Nullable LocaleGridRow row) {
        boolean hasSelection = row != null;
        renameButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
        undoDeleteButton.setEnabled(hasSelection && row.isDeleted());
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
            detailTitle.setText("편집할 Row를 선택하세요.");
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
        translationTable.getDiagnostics().addAll(translationTable.getSourceDiagnostics());
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
            String oldName = lastModifiedState ? EDITING_EDITOR_NAME : EDITOR_NAME;
            String newName = nextModifiedState ? EDITING_EDITOR_NAME : EDITOR_NAME;
            changeSupport.firePropertyChange(FileEditor.PROP_MODIFIED, lastModifiedState, nextModifiedState);
            changeSupport.firePropertyChange("name", oldName, newName);
            lastModifiedState = nextModifiedState;
        }
        FileEditorManager.getInstance(project).updateFilePresentation(file);
    }

    private void updateStatus() {
        long added = translationTable.getRows().stream().filter(LocaleGridFileEditor::isPendingAddedRow).count();
        long edited = translationTable.getRows().stream().filter(LocaleGridFileEditor::isPendingEditedRow).count();
        long deleted = translationTable.getRows().stream().filter(LocaleGridFileEditor::isPendingDeletedRow).count();
        long errors = translationTable.getRows().stream().filter(model::hasError).count();
        long warnings = translationTable.getRows().stream().filter(model::hasWarning).count();
        statusLabel.setText(
            "<html><b>카테고리: " + escapeHtml(translationTable.getCategory()) + "</b>"
                + " | 언어: " + escapeHtml(String.join(", ", translationTable.getLocales()))
                + " | Row: " + translationTable.getRows().size()
                + " | 추가: " + added
                + ", 편집: " + edited
                + ", 삭제: " + deleted
                + " | 에러: " + errors
                + ", 경고: " + warnings
                + "</html>"
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

    private static String escapeHtml(String text) {
        return text == null ? "" : text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
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
        return out.length() == 0 ? "적용에 실패했습니다." : out.toString();
    }

    private static String summarizeSave(SaveResult result) {
        return "적용된 파일: " + result.getWrittenFiles().size()
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
        return hasPendingRowChanges() ? EDITING_EDITOR_NAME : EDITOR_NAME;
    }

    @Override
    public void selectNotify() {
        if (skipNextSelectReload) {
            skipNextSelectReload = false;
            return;
        }
        if (isModified()) {
            return;
        }
        reload(captureViewState());
    }

    @Override
    public void deselectNotify() {
        if (!isModified() || suppressUnsavedPrompt) {
            return;
        }

        suppressUnsavedPrompt = true;
        int answer = Messages.showYesNoDialog(
            project,
            "저장되지 않은 다국어 에디터 변경 사항이 있습니다.\n"
                + "저장 전까지 JSON 원본 탭에는 이 변경 사항이 반영되지 않습니다.\n"
                + "JSON 탭으로 이동할까요?",
            "LocaleGrid",
            "JSON으로 이동",
            "계속 편집",
            Messages.getWarningIcon()
        );
        if (answer == Messages.YES) {
            suppressUnsavedPrompt = false;
            return;
        }

        SwingUtilities.invokeLater(() -> {
            FileEditorManager.getInstance(project).setSelectedEditor(file, "locale-grid");
            suppressUnsavedPrompt = false;
        });
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
    }

    @Override
    public boolean isModified() {
        return hasPendingRowChanges();
    }

    private boolean hasPendingRowChanges() {
        return translationTable != null && translationTable.getRows().stream().anyMatch(LocaleGridFileEditor::hasPendingRowChange);
    }

    private static boolean hasPendingRowChange(LocaleGridRow row) {
        return isPendingAddedRow(row) || isPendingEditedRow(row) || isPendingDeletedRow(row);
    }

    private static boolean isPendingAddedRow(LocaleGridRow row) {
        return row.isAdded() && !row.isDeleted();
    }

    private static boolean isPendingEditedRow(LocaleGridRow row) {
        return !row.isAdded() && !row.isDeleted() && row.isModified();
    }

    private static boolean isPendingDeletedRow(LocaleGridRow row) {
        return row.isDeleted();
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

    private static final class BottomActionButton extends JButton {
        private final Color normalColor;
        private final Color hoverColor;
        private final Color pressedColor;
        private final Color borderColor;

        private BottomActionButton(String text, Color normalColor, Color hoverColor, Color pressedColor, Color borderColor) {
            super(text);
            this.normalColor = normalColor;
            this.hoverColor = hoverColor;
            this.pressedColor = pressedColor;
            this.borderColor = borderColor;
            setForeground(new Color(248, 249, 250));
            setFont(getFont().deriveFont(Font.BOLD, 13f));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(96, 34));
            setMinimumSize(new Dimension(96, 34));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                ButtonModel model = getModel();
                Color fill = model.isPressed() ? pressedColor : model.isRollover() ? hoverColor : normalColor;
                g.setColor(fill);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g.setColor(borderColor);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private static final class StatusFilterButton extends JToggleButton {
        private static final Font BADGE_FONT = new Font(Font.MONOSPACED, Font.BOLD, 11);
        private static final Color SELECTED_TEXT = Color.WHITE;
        private static final Color IDLE_TEXT = new Color(195, 200, 204);
        private static final Color IDLE_BACKGROUND = new Color(68, 72, 74);
        private static final Color IDLE_BORDER = new Color(96, 101, 104);

        private final String code;

        private StatusFilterButton(String code) {
            super(code);
            this.code = code;
            setToolTipText(LocaleGridStatusRenderer.tooltipText(code));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(54, 24));
            setMinimumSize(new Dimension(54, 24));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                ButtonModel model = getModel();
                boolean active = isSelected();
                Color base = active ? LocaleGridStatusRenderer.badgeColor(code) : IDLE_BACKGROUND;
                if (model.isPressed()) {
                    base = base.darker();
                } else if (model.isRollover()) {
                    base = active ? base.brighter() : new Color(80, 85, 88);
                }

                int x = 1;
                int y = 2;
                int width = getWidth() - 2;
                int height = getHeight() - 4;
                g.setColor(base);
                g.fillRoundRect(x, y, width, height, 6, 6);
                g.setColor(active ? base.brighter() : IDLE_BORDER);
                g.drawRoundRect(x, y, width, height, 6, 6);

                g.setFont(BADGE_FONT);
                FontMetrics metrics = g.getFontMetrics();
                int textX = (getWidth() - metrics.stringWidth(code)) / 2;
                int textY = ((getHeight() - metrics.getHeight()) / 2) + metrics.getAscent();
                g.setColor(active ? SELECTED_TEXT : IDLE_TEXT);
                g.drawString(code, textX, textY);
            } finally {
                g.dispose();
            }
        }
    }

    private static final class WideConfirmDialog extends DialogWrapper {
        private static final int BODY_WIDTH = 620;

        private final String message;
        private final Icon icon;

        private WideConfirmDialog(Project project, String title, String message, String okText, String cancelText, Icon icon) {
            super(project, true);
            this.message = message;
            this.icon = icon;
            setTitle(title);
            setOKButtonText(okText);
            setCancelButtonText(cancelText);
            setResizable(false);
            init();
        }

        private static boolean show(Project project, String title, String message, String okText, String cancelText, Icon icon) {
            return new WideConfirmDialog(project, title, message, okText, cancelText, icon).showAndGet();
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout(18, 0));
            panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 2, 4));

            JLabel iconLabel = new JLabel(icon);
            iconLabel.setVerticalAlignment(SwingConstants.TOP);
            panel.add(iconLabel, BorderLayout.WEST);

            JTextArea text = new JTextArea(message);
            text.setEditable(false);
            text.setFocusable(false);
            text.setLineWrap(false);
            text.setWrapStyleWord(false);
            text.setOpaque(false);
            text.setBorder(BorderFactory.createEmptyBorder());
            text.setFont(UIManager.getFont("Label.font"));
            text.setForeground(UIManager.getColor("Label.foreground"));

            FontMetrics metrics = text.getFontMetrics(text.getFont());
            int lineHeight = metrics.getHeight();
            int lines = message.split("\\R", -1).length;
            text.setPreferredSize(new Dimension(BODY_WIDTH, Math.max(lineHeight * lines + 2, 72)));
            panel.add(text, BorderLayout.CENTER);

            return panel;
        }
    }
}
