package com.localegrid.editor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ExcelExportWriter {
    private ExcelExportWriter() {
    }

    static void write(Path path, List<String> headers, List<List<String>> rows) throws IOException {
        try (OutputStream output = Files.newOutputStream(path);
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            writeEntry(zip, "[Content_Types].xml", contentTypes());
            writeEntry(zip, "_rels/.rels", rootRelationships());
            writeEntry(zip, "xl/workbook.xml", workbook());
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelationships());
            writeEntry(zip, "xl/styles.xml", styles());
            writeEntry(zip, "xl/worksheets/sheet1.xml", sheet(headers, rows));
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.stripLeading().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes() {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
            </Types>
            """;
    }

    private static String rootRelationships() {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
            """;
    }

    private static String workbook() {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                      xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="LocaleGrid" sheetId="1" r:id="rId1"/>
              </sheets>
            </workbook>
            """;
    }

    private static String workbookRelationships() {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>
            """;
    }

    private static String styles() {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <fonts count="2">
                <font><sz val="11"/><name val="Calibri"/></font>
                <font><b/><sz val="11"/><name val="Calibri"/></font>
              </fonts>
              <fills count="2">
                <fill><patternFill patternType="none"/></fill>
                <fill><patternFill patternType="gray125"/></fill>
              </fills>
              <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
              <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
              <cellXfs count="2">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment wrapText="1" vertical="top"/></xf>
                <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment wrapText="1" vertical="top"/></xf>
              </cellXfs>
              <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
            </styleSheet>
            """;
    }

    private static String sheet(List<String> headers, List<List<String>> rows) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");
        xml.append("  <sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>\n");
        appendColumns(xml, headers.size());
        xml.append("  <sheetData>\n");
        appendRow(xml, 1, headers, 1);
        for (int i = 0; i < rows.size(); i++) {
            appendRow(xml, i + 2, rows.get(i), 0);
        }
        xml.append("  </sheetData>\n");
        xml.append("  <autoFilter ref=\"A1:").append(columnName(Math.max(headers.size(), 1))).append(rows.size() + 1).append("\"/>\n");
        xml.append("</worksheet>\n");
        return xml.toString();
    }

    private static void appendColumns(StringBuilder xml, int columnCount) {
        xml.append("  <cols>\n");
        for (int i = 1; i <= columnCount; i++) {
            double width = i == 1 ? 12.0 : i == 2 ? 36.0 : 28.0;
            xml.append("    <col min=\"").append(i).append("\" max=\"").append(i).append("\" width=\"").append(width).append("\" customWidth=\"1\"/>\n");
        }
        xml.append("  </cols>\n");
    }

    private static void appendRow(StringBuilder xml, int rowIndex, List<String> values, int style) {
        xml.append("    <row r=\"").append(rowIndex).append("\">\n");
        for (int i = 0; i < values.size(); i++) {
            xml.append("      <c r=\"").append(columnName(i + 1)).append(rowIndex).append("\" t=\"inlineStr\" s=\"").append(style).append("\">");
            xml.append("<is><t xml:space=\"preserve\">").append(escape(values.get(i))).append("</t></is>");
            xml.append("</c>\n");
        }
        xml.append("    </row>\n");
    }

    private static String columnName(int index) {
        StringBuilder name = new StringBuilder();
        int current = index;
        while (current > 0) {
            current--;
            name.insert(0, (char) ('A' + (current % 26)));
            current /= 26;
        }
        return name.toString();
    }

    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> {
                    if (isXmlChar(ch)) {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static boolean isXmlChar(char ch) {
        return ch == 0x9 || ch == 0xA || ch == 0xD || (ch >= 0x20 && ch <= 0xD7FF) || (ch >= 0xE000 && ch <= 0xFFFD);
    }
}
