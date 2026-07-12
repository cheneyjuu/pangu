// 关联业务：验证开发环境模拟电子印章会生成可预览 PDF，并明确标注无法律效力。
package com.pangu.bootstrap.committee;

import com.pangu.domain.model.committee.CommitteeSealType;
import com.pangu.domain.repository.ElectronicSealProvider;
import com.pangu.infrastructure.seal.MockElectronicSealProvider;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockElectronicSealProviderTest {

    @Test
    void signedPdfContainsVisibleNoLegalEffectMark() throws Exception {
        MockElectronicSealProvider provider = new MockElectronicSealProvider();
        ElectronicSealProvider.ProvisionedSeal seal = provider.provision(
                new ElectronicSealProvider.ProvisionRequest(
                        10001L, "第三届业委会模拟章", CommitteeSealType.OWNERS_COMMITTEE, "第三届业委会-2026"));

        ElectronicSealProvider.SignedDocument signed = provider.sign(
                new ElectronicSealProvider.SignRequest(
                        seal.providerSealId(), seal.certificateSerial(), "approval.pdf",
                        onePagePdf(), "source-sha256"));

        assertEquals("application/pdf", signed.contentType());
        assertEquals(MockElectronicSealProvider.VERIFICATION_STATUS, signed.verificationStatus());
        try (PDDocument document = Loader.loadPDF(signed.content())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("MOCK ELECTRONIC SEAL"));
            assertTrue(text.contains("NO LEGAL EFFECT"));
        }
    }

    private byte[] onePagePdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
