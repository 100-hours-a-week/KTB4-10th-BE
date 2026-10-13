package com.ktb10.kgb.guidebook.dto.request;

import jakarta.validation.constraints.NotNull;

/** 가이드북 파일 내보내기 요청입니다. */
public record GuidebookExportRequest(
        @NotNull GuidebookExportFormat format) {
}
