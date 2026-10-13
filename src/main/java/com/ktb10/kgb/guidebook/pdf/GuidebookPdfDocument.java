package com.ktb10.kgb.guidebook.pdf;

import java.util.Arrays;
import java.util.Objects;

/** Chromium 변환을 마친 PDF 바이트와 다운로드 파일명을 전달합니다. */
public final class GuidebookPdfDocument {

    private final byte[] content;
    private final String filename;

    public GuidebookPdfDocument(byte[] content, String filename) {
        Objects.requireNonNull(content, "PDF 바이트는 null일 수 없습니다.");
        if (content.length == 0) {
            throw new IllegalArgumentException("PDF 바이트는 비어 있을 수 없습니다.");
        }
        this.content = Arrays.copyOf(content, content.length);
        this.filename = requireText(filename, "PDF 파일명");
    }

    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    public String filename() {
        return filename;
    }

    public int size() {
        return content.length;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은 비어 있을 수 없습니다.");
        }
        return value;
    }
}
