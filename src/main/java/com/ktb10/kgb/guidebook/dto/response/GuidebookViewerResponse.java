package com.ktb10.kgb.guidebook.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** HTML 뷰어에 필요한 가이드북 본문과 버전 정보입니다. */
public record GuidebookViewerResponse(
        @JsonProperty("guidebook_id")
        Long guidebookId,
        @JsonProperty("content_html")
        String contentHtml,
        Integer version,
        @JsonProperty("updated_at")
        OffsetDateTime updatedAt) {

    public static GuidebookViewerResponse from(Guidebook guidebook, String contentHtml) {
        return new GuidebookViewerResponse(
                guidebook.getId(),
                contentHtml,
                guidebook.getVersion(),
                guidebook.getUpdatedAt().atOffset(ZoneOffset.UTC));
    }
}
