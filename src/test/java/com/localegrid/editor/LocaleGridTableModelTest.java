package com.localegrid.editor;

import com.localegrid.model.Diagnostic;
import com.localegrid.model.LocaleGridRow;
import com.localegrid.model.LocaleValue;
import com.localegrid.model.TranslationTable;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocaleGridTableModelTest {
    @Test
    void modifiedStatusTakesPrecedenceOverWarning() {
        TranslationTable table = new TranslationTable("login", "ko", new File("."), List.of("ko"));
        LocaleGridRow row = new LocaleGridRow("login.title", false);
        LocaleValue value = LocaleValue.stringValue("로그인", true);
        row.putValue("ko", value);
        table.getRows().add(row);
        table.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.WARNING, "빈 값이 있습니다.", "login.title"));

        LocaleGridTableModel model = new LocaleGridTableModel();
        model.setTable(table);
        value.setText("로그인 수정");

        assertEquals("편집", model.getStatusCode(row));
    }
}
