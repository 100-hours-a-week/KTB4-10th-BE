package com.ktb10.kgb.guidebook.service;

import com.ktb10.kgb.guidebook.dto.response.GuidebookViewerResponse;
import com.ktb10.kgb.guidebook.pdf.GuidebookPdfConverter;
import com.ktb10.kgb.guidebook.pdf.GuidebookPdfDocument;
import org.springframework.stereotype.Service;

/** 가이드북 접근 권한을 확인한 뒤 DB 트랜잭션 밖에서 PDF 변환을 수행합니다. */
@Service
public class GuidebookPdfService {

    private final GuidebookService guidebookService;
    private final GuidebookPdfConverter pdfConverter;

    public GuidebookPdfService(
            GuidebookService guidebookService,
            GuidebookPdfConverter pdfConverter) {
        this.guidebookService = guidebookService;
        this.pdfConverter = pdfConverter;
    }

    public GuidebookPdfDocument export(Long memberId, Long guidebookId) {
        GuidebookViewerResponse viewer = guidebookService.getGuidebookViewer(
                memberId, guidebookId);
        byte[] pdf = pdfConverter.convert(guidebookId, viewer.contentHtml());
        return new GuidebookPdfDocument(pdf, "guidebook-" + guidebookId + ".pdf");
    }
}
