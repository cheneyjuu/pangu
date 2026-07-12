// 关联业务：在开发和测试环境模拟电子印章制发、签章和验签，输出文件明确标注无法律效力。
package com.pangu.infrastructure.seal;

import com.pangu.domain.repository.ElectronicSealProvider;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Profile({"dev", "test"})
@ConditionalOnProperty(name = "platform.electronic-seal.provider", havingValue = "mock", matchIfMissing = true)
public class MockElectronicSealProvider implements ElectronicSealProvider {

    public static final String VERIFICATION_STATUS = "SIMULATED_NO_LEGAL_EFFECT";

    @Override
    public String providerCode() {
        return "MOCK";
    }

    @Override
    public boolean simulated() {
        return true;
    }

    @Override
    public ProvisionedSeal provision(ProvisionRequest request) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        LocalDateTime now = LocalDateTime.now();
        return new ProvisionedSeal("MOCK-SEAL-" + suffix, "MOCK-CERT-" + suffix, now, now.plusYears(1));
    }

    @Override
    public SignedDocument sign(SignRequest request) {
        if (request.sourceContent() == null || request.sourceContent().length == 0) {
            throw new IllegalArgumentException("待签章 PDF 为空");
        }
        String transactionId = "MOCK-SIGN-" + UUID.randomUUID().toString();
        return new SignedDocument(
                stampPdf(request.sourceContent(), transactionId),
                "application/pdf",
                transactionId,
                request.certificateSerial(),
                VERIFICATION_STATUS);
    }

    @Override
    public VerificationResult verify(VerificationRequest request) {
        validatePdf(request.content());
        return new VerificationResult(
                "MOCK-VERIFY-" + UUID.randomUUID(),
                "MOCK-EXTERNAL-CERT",
                VERIFICATION_STATUS);
    }

    private byte[] stampPdf(byte[] source, String transactionId) {
        try (PDDocument document = Loader.loadPDF(source);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (document.getNumberOfPages() == 0) {
                throw new IllegalArgumentException("待签章 PDF 不包含页面");
            }
            PDPage page = document.getPage(document.getNumberOfPages() - 1);
            float x = Math.max(20, page.getMediaBox().getWidth() - 210);
            float y = 28;
            try (PDPageContentStream stream = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                stream.setStrokingColor(new Color(198, 34, 34));
                stream.setNonStrokingColor(new Color(198, 34, 34));
                stream.setLineWidth(2f);
                stream.addRect(x, y, 180, 66);
                stream.stroke();
                writeLine(stream, x + 10, y + 45, 10, "MOCK ELECTRONIC SEAL");
                writeLine(stream, x + 10, y + 29, 8, "NO LEGAL EFFECT");
                writeLine(stream, x + 10, y + 13, 6, transactionId.substring(0, 24));
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalArgumentException("模拟电子签章仅支持有效 PDF 文件", ex);
        }
    }

    private void writeLine(PDPageContentStream stream, float x, float y, float size, String text)
            throws IOException {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), size);
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
    }

    private void validatePdf(byte[] content) {
        try (PDDocument ignored = Loader.loadPDF(content)) {
            // 能被 PDFBox 完整打开即视为开发环境模拟验签通过；不代表真实证书有效。
        } catch (IOException ex) {
            throw new IllegalArgumentException("上传文件不是有效 PDF", ex);
        }
    }
}
