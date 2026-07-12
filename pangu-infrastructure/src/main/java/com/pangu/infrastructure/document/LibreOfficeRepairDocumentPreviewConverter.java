package com.pangu.infrastructure.document;

import com.pangu.domain.repository.RepairDocumentPreviewConverter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class LibreOfficeRepairDocumentPreviewConverter implements RepairDocumentPreviewConverter {

    private static final long MAX_PDF_SIZE = 50L * 1024 * 1024;
    private static final int MAX_PREVIEW_PAGES = 30;

    private final String sofficeCommand;
    private final long timeoutSeconds;
    private final String cjkFontFamily;

    public LibreOfficeRepairDocumentPreviewConverter(
            @Value("${platform.document-preview.excel.soffice-command:soffice}") String sofficeCommand,
            @Value("${platform.document-preview.excel.timeout-seconds:45}") long timeoutSeconds,
            @Value("${platform.document-preview.excel.cjk-font-family:Noto Sans CJK SC}") String cjkFontFamily) {
        if (sofficeCommand == null || sofficeCommand.isBlank()) {
            throw new IllegalArgumentException("LibreOffice command must not be blank");
        }
        this.sofficeCommand = sofficeCommand.trim();
        this.timeoutSeconds = Math.max(5, Math.min(timeoutSeconds, 300));
        this.cjkFontFamily = normalizeFontFamily(cjkFontFamily);
    }

    @Override
    public byte[] convertExcelToPdf(String originalFileName, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Excel 原件为空");
        }
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("pangu-excel-preview-");
            Path profile = Files.createDirectory(workspace.resolve("profile"));
            Path output = Files.createDirectory(workspace.resolve("output"));
            Path fontCache = Files.createDirectory(workspace.resolve("font-cache"));
            Path fontConfig = workspace.resolve("fonts.conf");
            String extension = "application/vnd.ms-excel".equals(contentType) ? "xls" : "xlsx";
            Path source = workspace.resolve("source." + extension);
            Path target = output.resolve("source.pdf");
            Path processLog = workspace.resolve("libreoffice.log");
            Files.write(source, content);
            Files.writeString(fontConfig, fontConfig(fontCache), StandardCharsets.UTF_8);

            List<String> command = List.of(
                    sofficeCommand,
                    "--headless",
                    "--nologo",
                    "--nodefault",
                    "--nofirststartwizard",
                    "--norestore",
                    "-env:UserInstallation=" + profile.toUri(),
                    "--convert-to", "pdf:calc_pdf_Export",
                    "--outdir", output.toString(),
                    source.toString());
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(processLog.toFile());
            processBuilder.environment().put("FONTCONFIG_FILE", fontConfig.toString());
            processBuilder.environment().put("XDG_CACHE_HOME", fontCache.toString());
            Process process = processBuilder.start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(3, TimeUnit.SECONDS);
                }
                throw new IllegalStateException("Excel 转 PDF 超时");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(target)) {
                throw new IllegalStateException("Excel 转 PDF 失败: " + readLog(processLog));
            }
            byte[] pdf = Files.readAllBytes(target);
            if (pdf.length < 5 || pdf.length > MAX_PDF_SIZE
                    || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F' || pdf[4] != '-') {
                throw new IllegalStateException("Excel 转换结果不是有效 PDF");
            }
            return pdf;
        } catch (IOException ex) {
            throw new IllegalStateException("无法执行 Excel 转 PDF", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Excel 转 PDF 被中断", ex);
        } finally {
            deleteTree(workspace);
        }
    }

    @Override
    public List<byte[]> renderPdfPages(byte[] content) {
        if (content == null || content.length == 0 || content.length > MAX_PDF_SIZE) {
            throw new IllegalArgumentException("PDF 原件为空或过大");
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.getNumberOfPages() > MAX_PREVIEW_PAGES) {
                throw new IllegalArgumentException("PDF 超过 30 页，无法生成移动端预览");
            }
            PDFRenderer renderer = new PDFRenderer(document);
            java.util.ArrayList<byte[]> pages = new java.util.ArrayList<>(document.getNumberOfPages());
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(renderer.renderImageWithDPI(index, 144, ImageType.RGB), "png", output);
                pages.add(output.toByteArray());
            }
            return List.copyOf(pages);
        } catch (IOException ex) {
            throw new IllegalStateException("PDF 分页图片生成失败", ex);
        }
    }

    private String readLog(Path processLog) {
        try {
            String value = Files.exists(processLog)
                    ? Files.readString(processLog, StandardCharsets.UTF_8).trim()
                    : "未生成转换日志";
            return value.length() > 500 ? value.substring(0, 500) : value;
        } catch (IOException ignored) {
            return "转换日志读取失败";
        }
    }

    private String normalizeFontFamily(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.matches("[\\p{L}\\p{N} ._-]{1,80}") ? normalized : "Noto Sans CJK SC";
    }

    private String fontConfig(Path fontCache) {
        String preferredFont = xmlEscape(cjkFontFamily);
        String cachePath = xmlEscape(fontCache.toAbsolutePath().toString());
        return """
                <?xml version="1.0"?>
                <!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd">
                <fontconfig>
                  <dir>/usr/share/fonts</dir>
                  <dir>/usr/local/share/fonts</dir>
                  <dir>/System/Library/Fonts</dir>
                  <dir>/Library/Fonts</dir>
                  <dir>/System/Library/Assets/com_apple_MobileAsset_Font3</dir>
                  <dir>/System/Library/Assets/com_apple_MobileAsset_Font4</dir>
                  <dir prefix="xdg">fonts</dir>
                  <cachedir>%s</cachedir>
                  %s
                </fontconfig>
                """.formatted(cachePath, fontAliases(preferredFont));
    }

    private String fontAliases(String preferredFont) {
        String preferred = """
                <prefer>
                  <family>%s</family>
                  <family>Microsoft YaHei</family>
                  <family>Arial Unicode MS</family>
                  <family>Hiragino Sans GB</family>
                  <family>Songti SC</family>
                  <family>sans-serif</family>
                </prefer>
                """.formatted(preferredFont);
        return List.of("等线", "DengXian", "微软雅黑", "宋体", "SimSun", "黑体", "SimHei")
                .stream()
                .map(family -> "<alias><family>" + family + "</family>" + preferred + "</alias>")
                .reduce("", String::concat);
    }

    private String xmlEscape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary preview files are cleaned up on a best-effort basis.
                }
            });
        } catch (IOException ignored) {
            // Temporary preview files are cleaned up on a best-effort basis.
        }
    }
}
