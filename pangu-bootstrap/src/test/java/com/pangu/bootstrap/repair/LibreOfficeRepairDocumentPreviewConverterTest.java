package com.pangu.bootstrap.repair;

import com.pangu.infrastructure.document.LibreOfficeRepairDocumentPreviewConverter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LibreOfficeRepairDocumentPreviewConverterTest {

    @Test
    void convertsXlsxToPdfWithHeadlessLibreOffice() throws Exception {
        String command = locateSoffice();
        assumeTrue(command != null, "LibreOffice is not available in this environment");

        byte[] pdf = new LibreOfficeRepairDocumentPreviewConverter(command, 30, "Arial Unicode MS")
                .convertExcelToPdf(
                        "报价.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        minimalXlsx());

        assertTrue(pdf.length > 5);
        assertTrue(new String(pdf, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF-"));
    }

    @Test
    void rendersPdfPagesAsPngImages() throws Exception {
        byte[] source;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.save(output);
            source = output.toByteArray();
        }

        List<byte[]> pages = new LibreOfficeRepairDocumentPreviewConverter("soffice", 30, "Arial Unicode MS")
                .renderPdfPages(source);

        assertTrue(pages.size() == 2);
        assertTrue(pages.stream().allMatch(page -> page.length > 8
                && page[0] == (byte) 0x89 && page[1] == 'P' && page[2] == 'N' && page[3] == 'G'));
    }

    private String locateSoffice() throws Exception {
        List<String> candidates = List.of(
                System.getenv().getOrDefault("PANGU_LIBREOFFICE_COMMAND", "soffice"),
                "/Applications/LibreOffice.app/Contents/MacOS/soffice");
        for (String candidate : candidates) {
            if (candidate.contains("/") && !Files.isExecutable(Path.of(candidate))) {
                continue;
            }
            try {
                Process process = new ProcessBuilder(candidate, "--version").start();
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    return candidate;
                }
                process.destroyForcibly();
            } catch (IOException ignored) {
                // Try the next known command location.
            }
        }
        return null;
    }

    private byte[] minimalXlsx() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            add(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    </Types>
                    """);
            add(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);
            add(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets><sheet name="报价单" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                    """);
            add(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    </Relationships>
                    """);
            add(zip, "xl/worksheets/sheet1.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                      <sheetData>
                        <row r="1"><c r="A1" t="inlineStr"><is><t>维修项目</t></is></c><c r="B1" t="inlineStr"><is><t>报价</t></is></c></row>
                        <row r="2"><c r="A2" t="inlineStr"><is><t>管道维修</t></is></c><c r="B2" t="n"><v>18000</v></c></row>
                      </sheetData>
                    </worksheet>
                    """);
        }
        return output.toByteArray();
    }

    private void add(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
