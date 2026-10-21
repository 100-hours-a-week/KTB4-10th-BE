package com.ktb10.kgb.guidebook.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 기존 가이드북에 반영할 자연어 수정 요청입니다. */
public record GuidebookRegenerationRequest(
        @NotBlank
        @Size(max = 200)
        String feedback) {

    public GuidebookRegenerationRequest {
        feedback = feedback == null ? null : feedback.trim();
    }
}
