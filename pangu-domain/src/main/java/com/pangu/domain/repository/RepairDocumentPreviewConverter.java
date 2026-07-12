package com.pangu.domain.repository;

import java.util.List;

public interface RepairDocumentPreviewConverter {

    byte[] convertExcelToPdf(String originalFileName, String contentType, byte[] content);

    List<byte[]> renderPdfPages(byte[] content);
}
