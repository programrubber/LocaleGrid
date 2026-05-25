package com.localegrid.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.localegrid.core.DotPath;
import com.localegrid.core.SaveResult;
import com.localegrid.core.TableValidator;
import com.localegrid.core.TranslationTableLoader;
import com.localegrid.core.TranslationTableSaver;
import com.localegrid.model.Diagnostic;
import com.localegrid.model.LocaleGridRow;
import com.localegrid.model.LocaleValue;
import com.localegrid.model.TranslationTable;
import com.localegrid.settings.LocaleGridSettingsState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
            if (viewRow >= 0 && viewColumn >= 0 && model.isHandleColumn(convertColumnIndexToModel(viewColumn))) {
                return isOrderMoveAvailable() ? "드래그해서 순서 변경" : "검색/필터 중에는 순서를 변경할 수 없습니다.";
            }
            if (viewRow >= 0 && viewColumn >= 0 && model.isStatusColumn(convertColumnIndexToModel(viewColumn))) {
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

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            paintOrderDragIndicator((Graphics2D) graphics);
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
    private final JButton moveUpButton = new MoveActionButton(new MoveArrowIcon(true), "위로 이동");
    private final JButton moveDownButton = new MoveActionButton(new MoveArrowIcon(false), "아래로 이동");
    private final Map<String, JCheckBox> localeColumnChecks = new LinkedHashMap<>();
    private final JLabel statusLabel = new JLabel(" ");
    private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
    private TranslationTable translationTable;
    private LocaleGridRow dragSourceRow;
    private int dragTargetRow = -1;
    private boolean dragTargetBefore = true;
    private boolean dragMoved;
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
        JButton exceptionKeySettingsButton = new JButton("예외키 설정");
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

        rowActionsPanel.add(exceptionKeySettingsButton);
        rowActionsPanel.add(createActionSeparator());
        rowActionsPanel.add(moveUpButton);
        rowActionsPanel.add(moveDownButton);
        rowActionsPanel.add(createActionSeparator());
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
        exceptionKeySettingsButton.addActionListener(e -> openExceptionKeySettingsDialog());
        renameButton.addActionListener(e -> renameSelectedRow());
        deleteButton.addActionListener(e -> deleteSelectedRow());
        undoDeleteButton.addActionListener(e -> undoDeleteSelectedRow());
        moveUpButton.addActionListener(e -> moveSelectedRow(-1));
        moveDownButton.addActionListener(e -> moveSelectedRow(1));
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
        installOrderDragHandler();
        updateRowActionButtons(null);
    }

    private static JComponent createActionSeparator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(1, 24));
        return separator;
    }

    private void openExceptionKeySettingsDialog() {
        if (isModified()) {
            boolean confirmed = WideConfirmDialog.show(
                project,
                "예외키 설정",
                "예외키 설정을 변경하면 현재 다국어 에디터를 다시 불러옵니다.\n"
                    + "저장되지 않은 변경 사항은 사라집니다.\n"
                    + "예외키 설정을 변경할까요?",
                "설정 변경",
                "닫기",
                Messages.getWarningIcon()
            );
            if (!confirmed) {
                return;
            }
        }

        LocaleGridSettingsState settings = LocaleGridSettingsState.getInstance(project);
        String exceptionKeys = Messages.showInputDialog(
            project,
            exceptionKeySettingsMessage(),
            "예외키 설정",
            Messages.getQuestionIcon(),
            String.join(", ", settings.getExceptionKeyList()),
            null
        );
        if (exceptionKeys == null) {
            return;
        }

        settings.setExceptionKeysFromCsv(exceptionKeys);
        reload(captureViewState());
    }

    private String exceptionKeySettingsMessage() {
        return "번역 항목에서 제외할 최상위 키를 쉼표로 구분해 입력하세요. (예: __section__, __comment__)\n"
            + "예외키는 중복될 수 있으며, 저장 시 각 Locale 파일에서의 위치를 유지합니다.\n\n"
            + "예외키";
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
        if (grid.getColumnModel().getColumnCount() > LocaleGridTableModel.HANDLE_COLUMN) {
            grid.getColumnModel().getColumn(LocaleGridTableModel.HANDLE_COLUMN).setCellRenderer(new DragHandleRenderer());
        }
        if (grid.getColumnModel().getColumnCount() > LocaleGridTableModel.STATUS_COLUMN) {
            grid.getColumnModel().getColumn(LocaleGridTableModel.STATUS_COLUMN).setCellRenderer(new LocaleGridStatusRenderer());
        }
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
        grid.repaint();
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
        String key = Messages.showInputDialog(project, keyInputMessage(), "다국어 추가", Messages.getQuestionIcon());
        if (key == null) {
            return;
        }
        key = key.trim();
        String error = validateNewKey(key, null);
        if (error != null) {
            Messages.showErrorDialog(project, error, "다국어 추가");
            return;
        }

        boolean exceptionKey = isExceptionKey(key);
        LocaleGridRow row = new LocaleGridRow(
            key,
            exceptionKey ? LocaleGridRow.RowType.EXCEPTION_KEY : LocaleGridRow.RowType.TRANSLATION
        );
        row.markAdded();
        for (String locale : translationTable.getLocales()) {
            row.putValue(locale, LocaleValue.missing());
        }
        int insertAt = exceptionKey ? insertIndexForNewExceptionKeyRow(selectedRow()) : insertIndexForNewKey(key, selectedRow());
        translationTable.getRows().add(insertAt, row);
        validateCurrentTable();
        selectRow(row);
        updateModifiedState();
    }

    private int insertIndexForNewKey(String key, @Nullable LocaleGridRow selected) {
        List<LocaleGridRow> rows = translationTable.getRows();
        String group = topLevelGroup(key);
        int lastGroupIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            LocaleGridRow row = rows.get(i);
            if (!row.isExceptionKey() && group.equals(topLevelGroup(row.getKey()))) {
                lastGroupIndex = i;
            }
        }
        if (lastGroupIndex >= 0) {
            return lastGroupIndex + 1;
        }
        if (selected != null) {
            int selectedIndex = rows.indexOf(selected);
            if (selectedIndex >= 0) {
                return selectedIndex + 1;
            }
        }
        return rows.size();
    }

    private int insertIndexForNewExceptionKeyRow(@Nullable LocaleGridRow selected) {
        List<LocaleGridRow> rows = translationTable.getRows();
        if (selected == null) {
            return rows.size();
        }

        int selectedIndex = rows.indexOf(selected);
        if (selectedIndex < 0) {
            return rows.size();
        }
        if (selected.isExceptionKey()) {
            return selectedIndex + 1;
        }

        RowRange range = topLevelGroupIndexRange(topLevelGroup(selected.getKey()));
        return range == null ? selectedIndex + 1 : range.lastRow + 1;
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
                "다국어 편집"
            );
            return;
        }
        String nextKey = Messages.showInputDialog(project, keyInputMessage(), "다국어 편집", Messages.getQuestionIcon(), row.getKey(), null);
        if (nextKey == null) {
            return;
        }
        nextKey = nextKey.trim();
        String error = validateNewKey(nextKey, row);
        if (error != null) {
            Messages.showErrorDialog(project, error, "다국어 편집");
            return;
        }
        boolean wasExceptionKey = row.isExceptionKey();
        String previousGroup = wasExceptionKey ? null : topLevelGroup(row.getKey());
        boolean nextExceptionKey = isExceptionKey(nextKey);
        row.rename(nextKey);
        row.setExceptionKey(nextExceptionKey);
        if (nextExceptionKey) {
            relocateExceptionKeyRow(row, previousGroup);
        } else if (!wasExceptionKey) {
            relocateRenamedRow(row, previousGroup, topLevelGroup(nextKey));
        } else {
            relocateRenamedExceptionKeyToTranslation(row, topLevelGroup(nextKey));
        }
        validateCurrentTable();
        selectRow(row);
        updateModifiedState();
    }

    private void relocateRenamedRow(LocaleGridRow row, String previousGroup, String nextGroup) {
        if (previousGroup != null && previousGroup.equals(nextGroup)) {
            return;
        }

        List<LocaleGridRow> rows = translationTable.getRows();
        int currentIndex = rows.indexOf(row);
        if (currentIndex < 0) {
            return;
        }

        rows.remove(currentIndex);
        int lastTargetGroupIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            LocaleGridRow candidate = rows.get(i);
            if (!candidate.isExceptionKey() && nextGroup.equals(topLevelGroup(candidate.getKey()))) {
                lastTargetGroupIndex = i;
            }
        }

        if (lastTargetGroupIndex < 0) {
            rows.add(Math.min(currentIndex, rows.size()), row);
            return;
        }

        rows.add(lastTargetGroupIndex + 1, row);
        translationTable.markOrderChanged();
    }

    private void relocateRenamedExceptionKeyToTranslation(LocaleGridRow row, String nextGroup) {
        List<LocaleGridRow> rows = translationTable.getRows();
        int currentIndex = rows.indexOf(row);
        if (currentIndex < 0) {
            return;
        }

        rows.remove(currentIndex);
        int lastTargetGroupIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            LocaleGridRow candidate = rows.get(i);
            if (!candidate.isExceptionKey() && nextGroup.equals(topLevelGroup(candidate.getKey()))) {
                lastTargetGroupIndex = i;
            }
        }

        if (lastTargetGroupIndex < 0) {
            rows.add(Math.min(currentIndex, rows.size()), row);
        } else {
            rows.add(lastTargetGroupIndex + 1, row);
            translationTable.markOrderChanged();
        }
    }

    private void relocateExceptionKeyRow(LocaleGridRow row, @Nullable String previousGroup) {
        if (previousGroup == null) {
            return;
        }

        List<LocaleGridRow> rows = translationTable.getRows();
        int currentIndex = rows.indexOf(row);
        if (currentIndex < 0) {
            return;
        }

        rows.remove(currentIndex);
        int insertAt = rows.size();
        for (int i = 0; i < rows.size(); i++) {
            LocaleGridRow candidate = rows.get(i);
            if (!candidate.isExceptionKey() && previousGroup.equals(topLevelGroup(candidate.getKey()))) {
                insertAt = i + 1;
            }
        }
        rows.add(Math.min(insertAt, rows.size()), row);
        translationTable.markOrderChanged();
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

        boolean confirmed = WideApplyConfirmDialog.show(
            project,
            "LocaleGrid 적용",
            "현재 다국어 에디터의 key 순서를 기준으로 locale JSON을 다시 구성하고 적용할까요?",
            summarizePendingApply(createMissingFiles),
            "적용",
            "닫기",
            Messages.getQuestionIcon()
        );
        if (!confirmed) {
            return;
        }

        SaveResult result = new TranslationTableSaver().save(project, translationTable, createMissingFiles);
        if (result.hasErrors()) {
            Messages.showErrorDialog(project, summarizeDiagnostics(result), "LocaleGrid 적용");
            return;
        }

        reload(captureViewState());
    }

    private boolean hasMissingFilesWithValues() {
        for (String locale : translationTable.getLocales()) {
            File target = translationTable.getFilesByLocale().get(locale);
            if (target != null && !target.exists()) {
                return hasWritableValues(locale);
            }
        }
        return false;
    }

    private boolean hasWritableValues(String locale) {
        for (LocaleGridRow row : translationTable.getRows()) {
            LocaleValue value = row.getValue(locale);
            if (!row.isDeleted() && (value.isPresent() || !value.getDisplayText().isEmpty())) {
                return true;
            }
        }
        return false;
    }

    private void validateCurrentTable() {
        translationTable.getDiagnostics().clear();
        translationTable.getDiagnostics().addAll(translationTable.getActiveSourceDiagnostics());
        TableValidator.validate(translationTable);
        applyFilter();
        updateDetailPanel(selectedRow());
        updateStatus();
    }

    private String validateNewKey(String key, LocaleGridRow currentRow) {
        if (key == null || key.trim().isEmpty()) {
            return "key를 입력하세요.";
        }
        boolean exceptionKey = isExceptionKey(key);
        if (exceptionKey) {
            return null;
        }

        String error = DotPath.validate(key);
        if (error != null) {
            return error;
        }

        if (currentRow != null && key.equals(currentRow.getKey())) {
            return null;
        }

        Set<String> keys = new HashSet<>();
        for (LocaleGridRow row : translationTable.getRows()) {
            if (row == currentRow || row.isDeleted() || row.isExceptionKey()) {
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

    private String keyInputMessage() {
        return "key 명 (예: login.title)\n예외키: " + String.join(", ", LocaleGridSettingsState.getInstance(project).getExceptionKeyList());
    }

    private boolean isExceptionKey(String key) {
        return LocaleGridSettingsState.getInstance(project).isExceptionKey(key);
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
        moveUpButton.setEnabled(canMoveSelectedRow(row, -1));
        moveDownButton.setEnabled(canMoveSelectedRow(row, 1));
    }

    private boolean canMoveSelectedRow(@Nullable LocaleGridRow row, int direction) {
        if (row == null || translationTable == null || !isOrderMoveAvailable()) {
            return false;
        }
        int index = translationTable.getRows().indexOf(row);
        if (index < 0) {
            return false;
        }
        int targetIndex = index + direction;
        return targetIndex >= 0 && targetIndex < translationTable.getRows().size();
    }

    private boolean isOrderMoveAvailable() {
        return searchField.getText().trim().isEmpty()
            && !addedOnly.isSelected()
            && !warningOnly.isSelected()
            && !modifiedOnly.isSelected()
            && !deletedOnly.isSelected()
            && !errorOnly.isSelected();
    }

    private void moveSelectedRow(int direction) {
        LocaleGridRow row = selectedRow();
        if (!canMoveSelectedRow(row, direction)) {
            return;
        }
        int index = translationTable.getRows().indexOf(row);
        LocaleGridRow target = translationTable.getRows().get(index + direction);
        moveRows(row, target, direction < 0);
    }

    private void installOrderDragHandler() {
        grid.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                clearOrderDragFeedback();
                int viewRow = grid.rowAtPoint(event.getPoint());
                int viewColumn = grid.columnAtPoint(event.getPoint());
                if (viewRow < 0 || viewColumn < 0 || !isOrderMoveAvailable()) {
                    return;
                }
                if (!model.isHandleColumn(grid.convertColumnIndexToModel(viewColumn))) {
                    return;
                }
                dragSourceRow = model.getRow(viewRow);
                dragTargetRow = viewRow;
                dragTargetBefore = true;
                dragMoved = false;
                grid.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                grid.getSelectionModel().setSelectionInterval(viewRow, viewRow);
                grid.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                LocaleGridRow source = dragSourceRow;
                if (!dragMoved) {
                    updateOrderDragTarget(event);
                }
                int targetRow = dragTargetRow;
                boolean before = dragTargetBefore;
                clearOrderDragFeedback();
                if (source == null || translationTable == null || !isOrderMoveAvailable()) {
                    return;
                }
                if (targetRow < 0 || targetRow >= model.getRowCount()) {
                    return;
                }
                LocaleGridRow target = model.getRow(targetRow);
                if (source == target) {
                    return;
                }
                moveRows(source, target, before);
            }
        });
        grid.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                dragMoved = true;
                updateOrderDragTarget(event);
            }
        });
    }

    private void updateOrderDragTarget(MouseEvent event) {
        if (dragSourceRow == null || !isOrderMoveAvailable() || model.getRowCount() == 0) {
            return;
        }

        int nextRow = grid.rowAtPoint(event.getPoint());
        boolean nextBefore;
        if (nextRow < 0) {
            nextRow = event.getY() < 0 ? 0 : model.getRowCount() - 1;
            nextBefore = event.getY() < 0;
        } else {
            LocaleGridRow targetRow = model.getRow(nextRow);
            Rectangle targetBounds = shouldUseGroupDropTarget(dragSourceRow, targetRow)
                ? topLevelGroupBounds(topLevelGroup(targetRow.getKey()))
                : grid.getCellRect(nextRow, LocaleGridTableModel.HANDLE_COLUMN, true);
            nextBefore = event.getY() < targetBounds.y + targetBounds.height / 2;
        }

        if (dragTargetRow == nextRow && dragTargetBefore == nextBefore) {
            return;
        }
        dragTargetRow = nextRow;
        dragTargetBefore = nextBefore;
        grid.repaint();
    }

    private void clearOrderDragFeedback() {
        dragSourceRow = null;
        dragTargetRow = -1;
        dragTargetBefore = true;
        dragMoved = false;
        grid.setCursor(Cursor.getDefaultCursor());
        grid.repaint();
    }

    private void paintOrderDragIndicator(Graphics2D graphics) {
        if (dragSourceRow == null || dragTargetRow < 0 || dragTargetRow >= model.getRowCount()) {
            return;
        }

        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintDraggedSourceRows(g);

            DropTargetPaintArea targetArea = createDropTargetPaintArea();
            paintDropTargetRows(g, targetArea);
        } finally {
            g.dispose();
        }
    }

    private DropTargetPaintArea createDropTargetPaintArea() {
        LocaleGridRow targetRow = model.getRow(dragTargetRow);
        if (shouldUseGroupDropTarget(dragSourceRow, targetRow)) {
            return createGroupDropTargetPaintArea(topLevelGroup(targetRow.getKey()));
        }
        return createRowDropTargetPaintArea(dragTargetRow);
    }

    private DropTargetPaintArea createGroupDropTargetPaintArea(String targetGroup) {
        RowRange range = topLevelGroupRowRange(targetGroup);
        if (range == null) {
            return createRowDropTargetPaintArea(dragTargetRow);
        }

        Rectangle firstCell = grid.getCellRect(range.firstRow, LocaleGridTableModel.HANDLE_COLUMN, true);
        Rectangle lastCell = grid.getCellRect(range.lastRow, LocaleGridTableModel.HANDLE_COLUMN, true);
        Rectangle bounds = new Rectangle(0, firstCell.y, grid.getWidth(), lastCell.y + lastCell.height - firstCell.y);
        int edgeY = dragTargetBefore ? bounds.y : bounds.y + bounds.height - 1;
        Rectangle accent = dragTargetBefore
            ? new Rectangle(0, firstCell.y, grid.getWidth(), Math.max(1, firstCell.height / 2))
            : new Rectangle(0, lastCell.y + lastCell.height / 2, grid.getWidth(), Math.max(1, lastCell.height - lastCell.height / 2));
        return new DropTargetPaintArea(bounds, accent, edgeY);
    }

    private Rectangle topLevelGroupBounds(String targetGroup) {
        RowRange range = topLevelGroupRowRange(targetGroup);
        if (range == null) {
            return grid.getCellRect(dragTargetRow, LocaleGridTableModel.HANDLE_COLUMN, true);
        }
        Rectangle firstCell = grid.getCellRect(range.firstRow, LocaleGridTableModel.HANDLE_COLUMN, true);
        Rectangle lastCell = grid.getCellRect(range.lastRow, LocaleGridTableModel.HANDLE_COLUMN, true);
        return new Rectangle(0, firstCell.y, grid.getWidth(), lastCell.y + lastCell.height - firstCell.y);
    }

    private static boolean shouldUseGroupDropTarget(LocaleGridRow source, LocaleGridRow target) {
        return !target.isExceptionKey() && (source.isExceptionKey() || !topLevelGroup(source.getKey()).equals(topLevelGroup(target.getKey())));
    }

    private static boolean isGroupMove(LocaleGridRow source, LocaleGridRow target) {
        return !source.isExceptionKey() && (target.isExceptionKey() || !topLevelGroup(source.getKey()).equals(topLevelGroup(target.getKey())));
    }

    private @Nullable RowRange topLevelGroupRowRange(String targetGroup) {
        int firstRow = -1;
        int lastRow = -1;
        for (int rowIndex = 0; rowIndex < model.getRowCount(); rowIndex++) {
            LocaleGridRow row = model.getRow(rowIndex);
            if (row.isExceptionKey()) {
                continue;
            }
            if (!targetGroup.equals(topLevelGroup(row.getKey()))) {
                continue;
            }
            if (firstRow < 0) {
                firstRow = rowIndex;
            }
            lastRow = rowIndex;
        }
        return firstRow < 0 ? null : new RowRange(firstRow, lastRow);
    }

    private DropTargetPaintArea createRowDropTargetPaintArea(int targetRow) {
        Rectangle rowBounds = grid.getCellRect(targetRow, LocaleGridTableModel.HANDLE_COLUMN, true);
        Rectangle bounds = new Rectangle(0, rowBounds.y, grid.getWidth(), rowBounds.height);
        int edgeY = dragTargetBefore ? bounds.y : bounds.y + bounds.height - 1;
        int accentY = dragTargetBefore ? bounds.y : bounds.y + bounds.height / 2;
        int accentHeight;
        if (dragTargetBefore) {
            accentHeight = Math.max(1, bounds.height / 2);
        } else {
            accentHeight = Math.max(1, bounds.y + bounds.height - accentY);
        }
        Rectangle accent = new Rectangle(0, accentY, grid.getWidth(), accentHeight);
        return new DropTargetPaintArea(bounds, accent, edgeY);
    }

    private void paintDropTargetRows(Graphics2D g, DropTargetPaintArea targetArea) {
        Rectangle bounds = targetArea.bounds;
        Rectangle accent = targetArea.accent;
        int edgeY = targetArea.edgeY;

        Composite oldComposite = g.getComposite();
        Stroke oldStroke = g.getStroke();

        g.setComposite(AlphaComposite.SrcOver.derive(0.18f));
        g.setColor(new Color(60, 124, 202));
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        g.setComposite(AlphaComposite.SrcOver.derive(0.34f));
        g.setColor(new Color(32, 170, 238));
        g.fillRect(accent.x, accent.y, accent.width, accent.height);

        g.setComposite(oldComposite);
        g.setColor(new Color(114, 188, 255));
        g.setStroke(new BasicStroke(1.6f));
        g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);

        int bandHeight = 8;
        int bandY = Math.max(bounds.y - bandHeight / 2, Math.min(edgeY - bandHeight / 2, bounds.y + bounds.height - bandHeight));
        g.setComposite(AlphaComposite.SrcOver.derive(0.28f));
        g.setColor(new Color(26, 188, 255));
        g.fillRoundRect(0, bandY - 2, grid.getWidth(), bandHeight + 4, 6, 6);

        g.setComposite(oldComposite);
        g.setColor(new Color(25, 58, 80));
        g.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(2, edgeY, grid.getWidth() - 3, edgeY);

        g.setColor(new Color(116, 219, 255));
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(2, edgeY, grid.getWidth() - 3, edgeY);

        g.setColor(new Color(145, 229, 255));
        int[] xPoints = {8, 24, 8};
        int[] yPoints = {edgeY - 9, edgeY, edgeY + 9};
        g.fillPolygon(xPoints, yPoints, xPoints.length);
        g.setColor(new Color(25, 58, 80));
        g.setStroke(new BasicStroke(1.4f));
        g.drawPolygon(xPoints, yPoints, xPoints.length);

        g.setStroke(oldStroke);
        g.setComposite(oldComposite);
    }

    private void paintDraggedSourceRows(Graphics2D g) {
        LocaleGridRow targetRow = model.getRow(dragTargetRow);
        boolean groupMove = isGroupMove(dragSourceRow, targetRow);
        String sourceGroup = dragSourceRow.isExceptionKey() ? null : topLevelGroup(dragSourceRow.getKey());

        Composite oldComposite = g.getComposite();
        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(1.5f));

        for (int rowIndex = 0; rowIndex < model.getRowCount(); rowIndex++) {
            LocaleGridRow row = model.getRow(rowIndex);
            boolean moving = groupMove
                ? !row.isExceptionKey() && sourceGroup.equals(topLevelGroup(row.getKey()))
                : row == dragSourceRow;
            if (!moving) {
                continue;
            }

            Rectangle cell = grid.getCellRect(rowIndex, LocaleGridTableModel.HANDLE_COLUMN, true);
            Rectangle rowBounds = new Rectangle(0, cell.y, grid.getWidth(), cell.height);
            g.setComposite(AlphaComposite.SrcOver.derive(0.26f));
            g.setColor(new Color(232, 147, 55));
            g.fillRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);
            g.setComposite(oldComposite);
            g.setColor(new Color(255, 177, 79));
            g.drawRect(rowBounds.x, rowBounds.y, rowBounds.width - 1, rowBounds.height - 1);
        }

        g.setStroke(oldStroke);
        g.setComposite(oldComposite);
    }

    private void moveRows(LocaleGridRow source, LocaleGridRow target, boolean beforeTarget) {
        if (translationTable == null || source == null || target == null || source == target) {
            return;
        }
        List<LocaleGridRow> rows = translationTable.getRows();
        if (!rows.contains(source) || !rows.contains(target)) {
            return;
        }

        List<LocaleGridRow> previousOrder = new ArrayList<>(rows);
        if (source.isExceptionKey()) {
            moveExceptionKeyRow(rows, source, target, beforeTarget);
        } else if (target.isExceptionKey()) {
            moveTopLevelGroupToRow(rows, topLevelGroup(source.getKey()), target, beforeTarget);
        } else {
            String sourceGroup = topLevelGroup(source.getKey());
            String targetGroup = topLevelGroup(target.getKey());
            if (sourceGroup.equals(targetGroup)) {
                moveSingleRow(rows, source, target, beforeTarget);
            } else {
                moveTopLevelGroup(rows, sourceGroup, targetGroup, beforeTarget);
            }
        }
        if (rows.equals(previousOrder)) {
            return;
        }
        translationTable.markOrderChanged();
        validateCurrentTable();
        selectRow(source);
        updateModifiedState();
    }

    private static void moveSingleRow(List<LocaleGridRow> rows, LocaleGridRow source, LocaleGridRow target, boolean beforeTarget) {
        int sourceIndex = rows.indexOf(source);
        int targetIndex = rows.indexOf(target);
        if (sourceIndex < 0 || targetIndex < 0) {
            return;
        }
        rows.remove(sourceIndex);
        targetIndex = rows.indexOf(target);
        int insertAt = beforeTarget ? targetIndex : targetIndex + 1;
        if (insertAt > rows.size()) {
            insertAt = rows.size();
        }
        rows.add(insertAt, source);
    }

    private static void moveExceptionKeyRow(List<LocaleGridRow> rows, LocaleGridRow source, LocaleGridRow target, boolean beforeTarget) {
        int sourceIndex = rows.indexOf(source);
        if (sourceIndex < 0) {
            return;
        }
        rows.remove(sourceIndex);

        int insertAt;
        if (target.isExceptionKey()) {
            int targetIndex = rows.indexOf(target);
            if (targetIndex < 0) {
                rows.add(source);
                return;
            }
            insertAt = beforeTarget ? targetIndex : targetIndex + 1;
        } else {
            RowRange targetRange = topLevelGroupIndexRange(rows, topLevelGroup(target.getKey()));
            if (targetRange == null) {
                rows.add(source);
                return;
            }
            insertAt = beforeTarget ? targetRange.firstRow : targetRange.lastRow + 1;
        }

        rows.add(Math.min(insertAt, rows.size()), source);
    }

    private static void moveTopLevelGroupToRow(List<LocaleGridRow> rows, String sourceGroup, LocaleGridRow target, boolean beforeTarget) {
        List<LocaleGridRow> moving = collectTopLevelGroup(rows, sourceGroup);
        if (moving.isEmpty()) {
            return;
        }
        rows.removeAll(moving);

        int targetIndex = rows.indexOf(target);
        if (targetIndex < 0) {
            rows.addAll(moving);
            return;
        }

        int insertAt = beforeTarget ? targetIndex : targetIndex + 1;
        rows.addAll(Math.min(insertAt, rows.size()), moving);
    }

    private static void moveTopLevelGroup(List<LocaleGridRow> rows, String sourceGroup, String targetGroup, boolean beforeTarget) {
        List<LocaleGridRow> moving = collectTopLevelGroup(rows, sourceGroup);
        if (moving.isEmpty()) {
            return;
        }
        rows.removeAll(moving);

        int targetStart = -1;
        int targetEnd = -1;
        for (int i = 0; i < rows.size(); i++) {
            LocaleGridRow row = rows.get(i);
            if (!row.isExceptionKey() && targetGroup.equals(topLevelGroup(row.getKey()))) {
                if (targetStart < 0) {
                    targetStart = i;
                }
                targetEnd = i;
            }
        }
        if (targetStart < 0) {
            rows.addAll(moving);
            return;
        }

        int insertAt = beforeTarget ? targetStart : targetEnd + 1;
        rows.addAll(insertAt, moving);
    }

    private static List<LocaleGridRow> collectTopLevelGroup(List<LocaleGridRow> rows, String sourceGroup) {
        List<LocaleGridRow> moving = new ArrayList<>();
        for (LocaleGridRow row : rows) {
            if (!row.isExceptionKey() && sourceGroup.equals(topLevelGroup(row.getKey()))) {
                moving.add(row);
            }
        }
        return moving;
    }

    private @Nullable RowRange topLevelGroupIndexRange(String targetGroup) {
        return topLevelGroupIndexRange(translationTable.getRows(), targetGroup);
    }

    private static @Nullable RowRange topLevelGroupIndexRange(List<LocaleGridRow> rows, String targetGroup) {
        int firstRow = -1;
        int lastRow = -1;
        for (int i = 0; i < rows.size(); i++) {
            LocaleGridRow row = rows.get(i);
            if (row.isExceptionKey() || !targetGroup.equals(topLevelGroup(row.getKey()))) {
                continue;
            }
            if (firstRow < 0) {
                firstRow = i;
            }
            lastRow = i;
        }
        return firstRow < 0 ? null : new RowRange(firstRow, lastRow);
    }

    private static String topLevelGroup(String key) {
        int dot = key.indexOf('.');
        return dot < 0 ? key : key.substring(0, dot);
    }

    private static final class DropTargetPaintArea {
        private final Rectangle bounds;
        private final Rectangle accent;
        private final int edgeY;

        private DropTargetPaintArea(Rectangle bounds, Rectangle accent, int edgeY) {
            this.bounds = bounds;
            this.accent = accent;
            this.edgeY = edgeY;
        }
    }

    private static final class RowRange {
        private final int firstRow;
        private final int lastRow;

        private RowRange(int firstRow, int lastRow) {
            this.firstRow = firstRow;
            this.lastRow = lastRow;
        }
    }

    private static final class PendingApplySummary {
        private int writtenFiles;
        private int createdFiles;
        private int addedKeys;
        private int modifiedKeys;
        private int deletedKeys;
        private boolean orderChanged;
    }

    private void selectRow(LocaleGridRow row) {
        int index = model.indexOf(row);
        if (index >= 0) {
            grid.getSelectionModel().setSelectionInterval(index, index);
            grid.scrollRectToVisible(grid.getCellRect(index, LocaleGridTableModel.KEY_COLUMN, true));
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
            editor.setEditable(!row.isDeleted() && value.isEditable());
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
        translationTable.getDiagnostics().addAll(translationTable.getActiveSourceDiagnostics());
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
        updateEditorTabTitle();
        FileEditorManager.getInstance(project).updateFilePresentation(file);
    }

    private void updateEditorTabTitle() {
        SwingUtilities.invokeLater(() -> {
            String name = getName();
            Component comp = root.getParent();
            while (comp != null) {
                if (comp instanceof JBTabs) {
                    JBTabs tabs = (JBTabs) comp;
                    for (TabInfo info : tabs.getTabs()) {
                        if (SwingUtilities.isDescendingFrom(root, info.getComponent())) {
                            if (!name.equals(info.getText())) {
                                info.setText(name);
                            }
                            return;
                        }
                    }
                }
                comp = comp.getParent();
            }
        });
    }

    private void updateStatus() {
        long added = translationTable.getRows().stream().filter(LocaleGridFileEditor::isPendingAddedRow).count();
        long edited = translationTable.getRows().stream().filter(LocaleGridFileEditor::isPendingEditedRow).count();
        long deleted = translationTable.getRows().stream().filter(LocaleGridFileEditor::isPendingDeletedRow).count();
        long errors = translationTable.getRows().stream().filter(model::hasError).count();
        long warnings = translationTable.getRows().stream().filter(model::hasWarning).count();
        String orderStatus = translationTable.isOrderChanged() ? " | 순서 변경 있음" : "";
        statusLabel.setText(
            "<html><b>카테고리: " + escapeHtml(translationTable.getCategory()) + "</b>"
                + " | 언어: " + escapeHtml(String.join(", ", translationTable.getLocales()))
                + " | Row: " + translationTable.getRows().size()
                + " | 추가: " + added
                + ", 편집: " + edited
                + ", 삭제: " + deleted
                + " | 에러: " + errors
                + ", 경고: " + warnings
                + orderStatus
                + "</html>"
        );
    }

    private void resizeColumns() {
        if (grid.getColumnModel().getColumnCount() == 0) {
            return;
        }
        grid.getColumnModel().getColumn(LocaleGridTableModel.HANDLE_COLUMN).setPreferredWidth(30);
        grid.getColumnModel().getColumn(LocaleGridTableModel.HANDLE_COLUMN).setMinWidth(28);
        grid.getColumnModel().getColumn(LocaleGridTableModel.HANDLE_COLUMN).setMaxWidth(34);
        grid.getColumnModel().getColumn(LocaleGridTableModel.STATUS_COLUMN).setPreferredWidth(64);
        grid.getColumnModel().getColumn(LocaleGridTableModel.STATUS_COLUMN).setMaxWidth(72);
        grid.getColumnModel().getColumn(LocaleGridTableModel.KEY_COLUMN).setPreferredWidth(280);
        for (int i = LocaleGridTableModel.KEY_COLUMN; i < grid.getColumnModel().getColumnCount(); i++) {
            if (i == LocaleGridTableModel.KEY_COLUMN) {
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

    private String summarizePendingApply(boolean createMissingFiles) {
        PendingApplySummary summary = createPendingApplySummary(createMissingFiles);
        return formatApplySummary(
            summary.writtenFiles,
            summary.createdFiles,
            summary.addedKeys,
            summary.modifiedKeys,
            summary.deletedKeys,
            summary.orderChanged
        );
    }

    private PendingApplySummary createPendingApplySummary(boolean createMissingFiles) {
        PendingApplySummary summary = new PendingApplySummary();
        summary.orderChanged = translationTable.isOrderChanged();

        for (String locale : translationTable.getLocales()) {
            File target = translationTable.getFilesByLocale().get(locale);
            if (target == null) {
                continue;
            }
            if (target.exists()) {
                summary.writtenFiles++;
                continue;
            }
            if (createMissingFiles && hasWritableValues(locale)) {
                summary.writtenFiles++;
                summary.createdFiles++;
            }
        }

        for (LocaleGridRow row : translationTable.getRows()) {
            if (row.isDeleted()) {
                boolean wasPresent = false;
                for (String locale : translationTable.getLocales()) {
                    if (row.getValue(locale).isPresent()) {
                        wasPresent = true;
                        break;
                    }
                }
                if (wasPresent) {
                    summary.deletedKeys++;
                }
            } else if (row.isAdded()) {
                summary.addedKeys++;
            } else if (row.isModified()) {
                summary.modifiedKeys++;
            }
        }
        return summary;
    }

    private static String summarizeSave(SaveResult result) {
        return formatApplySummary(
            result.getWrittenFiles().size(),
            result.getCreatedFiles().size(),
            result.getAddedKeys(),
            result.getModifiedKeys(),
            result.getDeletedKeys(),
            result.isOrderChanged()
        );
    }

    private static String formatApplySummary(
        int writtenFiles,
        int createdFiles,
        int addedKeys,
        int modifiedKeys,
        int deletedKeys,
        boolean orderChanged
    ) {
        List<String> parts = new ArrayList<>();
        appendSummaryPart(parts, "파일 저장", writtenFiles);
        appendSummaryPart(parts, "생성", createdFiles);
        appendSummaryPart(parts, "추가", addedKeys);
        appendSummaryPart(parts, "수정", modifiedKeys);
        appendSummaryPart(parts, "삭제", deletedKeys);
        if (orderChanged) {
            parts.add("순서 변경 있음");
        }
        return parts.isEmpty() ? "변경 없음" : String.join(" · ", parts);
    }

    private static void appendSummaryPart(List<String> parts, String label, int count) {
        if (count > 0) {
            parts.add(label + " " + count + "개");
        }
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

        SwingUtilities.invokeLater(() -> {
            if (project.isDisposed() || !file.isValid()) {
                return;
            }

            FileEditorManagerEx fem = FileEditorManagerEx.getInstanceEx(project);
            EditorComposite selectedComposite = findSelectedCompositeOwnedByThisEditor(fem);
            if (selectedComposite == null) {
                return;
            }

            FileEditor currentEditor = selectedComposite.getSelectedEditor();
            if (currentEditor == this || !(currentEditor instanceof LocaleGridJsonSourceEditor)) {
                return;
            }

            if (suppressUnsavedPrompt) {
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

            selectedComposite.setSelectedEditor("locale-grid");
            suppressUnsavedPrompt = false;
        });
    }

    private @Nullable EditorComposite findSelectedCompositeOwnedByThisEditor(FileEditorManagerEx fem) {
        for (EditorWindow window : fem.getWindows()) {
            if (window == null || window.isDisposed()) {
                continue;
            }

            EditorComposite selectedComposite = window.getSelectedComposite();
            if (selectedComposite == null || !file.equals(selectedComposite.getFile())) {
                continue;
            }

            if (selectedComposite.getAllEditors().contains(this)) {
                return selectedComposite;
            }
        }
        return null;
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
    }

    @Override
    public boolean isModified() {
        return hasPendingRowChanges();
    }

    private boolean hasPendingRowChanges() {
        return translationTable != null
            && (translationTable.isOrderChanged()
            || translationTable.getRows().stream().anyMatch(LocaleGridFileEditor::hasPendingRowChange));
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

    private static final class MoveArrowIcon implements Icon {
        private static final int SIZE = 18;
        private final boolean up;

        private MoveArrowIcon(boolean up) {
            this.up = up;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Color stroke = component.isEnabled() ? new Color(224, 231, 237) : new Color(116, 123, 128);
                g.setColor(stroke);

                int centerX = x + SIZE / 2;
                int top = y + 4;
                int bottom = y + SIZE - 5;
                int arrowY = up ? top : bottom;
                int wingY = up ? y + 9 : y + SIZE - 10;
                int baseY = up ? bottom : top;
                g.drawLine(centerX, arrowY, centerX, baseY);
                if (up) {
                    g.drawLine(centerX, arrowY, x + 5, wingY);
                    g.drawLine(centerX, arrowY, x + SIZE - 5, wingY);
                } else {
                    g.drawLine(centerX, arrowY, x + 5, wingY);
                    g.drawLine(centerX, arrowY, x + SIZE - 5, wingY);
                }
            } finally {
                g.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }

    private static final class MoveActionButton extends JButton {
        private static final Color NORMAL_FILL = new Color(68, 74, 78);
        private static final Color HOVER_FILL = new Color(82, 91, 97);
        private static final Color PRESSED_FILL = new Color(54, 61, 66);
        private static final Color DISABLED_FILL = new Color(58, 63, 66);
        private static final Color NORMAL_BORDER = new Color(99, 108, 115);
        private static final Color HOVER_BORDER = new Color(126, 145, 158);
        private static final Color DISABLED_BORDER = new Color(78, 84, 88);

        private MoveActionButton(Icon icon, String tooltip) {
            super(icon);
            setToolTipText(tooltip);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(32, 28));
            setMinimumSize(new Dimension(32, 28));
            setMaximumSize(new Dimension(32, 28));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                ButtonModel state = getModel();
                Color fill = !isEnabled()
                    ? DISABLED_FILL
                    : state.isPressed() ? PRESSED_FILL : state.isRollover() ? HOVER_FILL : NORMAL_FILL;
                Color border = !isEnabled()
                    ? DISABLED_BORDER
                    : state.isRollover() ? HOVER_BORDER : NORMAL_BORDER;

                int x = 1;
                int y = 2;
                int width = getWidth() - 2;
                int height = getHeight() - 4;
                g.setColor(fill);
                g.fillRoundRect(x, y, width, height, 7, 7);
                g.setColor(border);
                g.drawRoundRect(x, y, width, height, 7, 7);

                if (isEnabled() && state.isRollover()) {
                    g.setColor(new Color(255, 255, 255, 24));
                    g.drawLine(x + 4, y + 1, x + width - 4, y + 1);
                }
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private final class DragHandleRenderer extends JComponent implements TableCellRenderer {
        private static final Color ENABLED_DOT = new Color(118, 126, 132);
        private static final Color DISABLED_DOT = new Color(86, 91, 95);

        private JTable table;
        private boolean selected;
        private int row;

        private DragHandleRenderer() {
            setOpaque(true);
            setToolTipText("드래그해서 순서 변경");
        }

        @Override
        public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column
        ) {
            this.table = table;
            this.selected = isSelected;
            this.row = row;
            setToolTipText(isOrderMoveAvailable() ? "드래그해서 순서 변경" : "검색/필터 중에는 순서를 변경할 수 없습니다.");
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                Color background = selected
                    ? table.getSelectionBackground()
                    : row % 2 == 0 ? new Color(60, 64, 65) : new Color(56, 60, 61);
                g.setColor(background);
                g.fillRect(0, 0, getWidth(), getHeight());

                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(isOrderMoveAvailable() ? ENABLED_DOT : DISABLED_DOT);
                int dot = 3;
                int gap = 5;
                int startX = (getWidth() - dot * 2 - gap) / 2;
                int startY = (getHeight() - dot * 3 - gap * 2) / 2;
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 2; x++) {
                        g.fillOval(startX + x * (dot + gap), startY + y * (dot + gap), dot, dot);
                    }
                }
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

    private static final class WideApplyConfirmDialog extends DialogWrapper {
        private static final int BODY_WIDTH = 720;

        private final String message;
        private final String summary;
        private final Icon icon;

        private WideApplyConfirmDialog(
            Project project,
            String title,
            String message,
            String summary,
            String applyText,
            String closeText,
            Icon icon
        ) {
            super(project, true);
            this.message = message;
            this.summary = summary;
            this.icon = icon;
            setTitle(title);
            setOKButtonText(applyText);
            setCancelButtonText(closeText);
            setResizable(false);
            init();
        }

        private static boolean show(
            Project project,
            String title,
            String message,
            String summary,
            String applyText,
            String closeText,
            Icon icon
        ) {
            return new WideApplyConfirmDialog(project, title, message, summary, applyText, closeText, icon).showAndGet();
        }

        @Override
        protected Action @NotNull [] createActions() {
            return new Action[] {getCancelAction(), getOKAction()};
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout(18, 0));
            panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 2, 4));

            JLabel iconLabel = new JLabel(icon);
            iconLabel.setVerticalAlignment(SwingConstants.TOP);
            panel.add(iconLabel, BorderLayout.WEST);

            JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 6));
            textPanel.setOpaque(false);
            textPanel.add(dialogLabel(message, Font.PLAIN));
            textPanel.add(dialogLabel(summary, Font.BOLD));
            textPanel.setPreferredSize(new Dimension(BODY_WIDTH, 48));
            panel.add(textPanel, BorderLayout.CENTER);

            return panel;
        }

        private static JLabel dialogLabel(String text, int style) {
            JLabel label = new JLabel(text);
            label.setFont(UIManager.getFont("Label.font").deriveFont(style));
            label.setForeground(UIManager.getColor("Label.foreground"));
            return label;
        }
    }

    private static final class WideInfoDialog extends DialogWrapper {
        private static final int BODY_WIDTH = 720;

        private final String message;
        private final String summary;
        private final Icon icon;

        private WideInfoDialog(Project project, String title, String message, String summary, Icon icon) {
            super(project, true);
            this.message = message;
            this.summary = summary;
            this.icon = icon;
            setTitle(title);
            setOKButtonText("OK");
            setResizable(false);
            init();
        }

        private static void show(Project project, String title, String message, String summary, Icon icon) {
            new WideInfoDialog(project, title, message, summary, icon).show();
        }

        @Override
        protected Action @NotNull [] createActions() {
            return new Action[] {getOKAction()};
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout(18, 0));
            panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 2, 4));

            JLabel iconLabel = new JLabel(icon);
            iconLabel.setVerticalAlignment(SwingConstants.TOP);
            panel.add(iconLabel, BorderLayout.WEST);

            JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 6));
            textPanel.setOpaque(false);
            textPanel.add(dialogLabel(message, Font.PLAIN));
            textPanel.add(dialogLabel(summary, Font.BOLD));
            textPanel.setPreferredSize(new Dimension(BODY_WIDTH, 48));
            panel.add(textPanel, BorderLayout.CENTER);

            return panel;
        }

        private static JLabel dialogLabel(String text, int style) {
            JLabel label = new JLabel(text);
            label.setFont(UIManager.getFont("Label.font").deriveFont(style));
            label.setForeground(UIManager.getColor("Label.foreground"));
            return label;
        }
    }
}
