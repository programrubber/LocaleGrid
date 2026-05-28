package com.localegrid.editor;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelExportWriterTest {
    @Test
    void writesXlsxZipWithVisibleTableValues() throws Exception {
        Path file = Files.createTempFile("localegrid-export", ".xlsx");

        ExcelExportWriter.write(
            file,
            List.of("상태", "key", "ko", "en"),
            List.of(List.of("편집", "login.title", "로그인", "Login & Sign in"))
        );

        try (ZipFile zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8)) {
            assertNotNull(zip.getEntry("[Content_Types].xml"));
            assertNotNull(zip.getEntry("xl/workbook.xml"));
            assertNotNull(zip.getEntry("xl/worksheets/sheet1.xml"));

            String sheet = new String(zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sheet.contains("login.title"));
            assertTrue(sheet.contains("로그인"));
            assertTrue(sheet.contains("Login &amp; Sign in"));
        }
    }
}
