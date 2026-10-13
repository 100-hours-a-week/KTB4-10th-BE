package com.ktb10.kgb.guidebook.pdf;

/** 완성형 가이드북 HTML을 PDF로 변환하는 경계입니다. */
public interface GuidebookPdfConverter {

    byte[] convert(Long guidebookId, String contentHtml);
}
