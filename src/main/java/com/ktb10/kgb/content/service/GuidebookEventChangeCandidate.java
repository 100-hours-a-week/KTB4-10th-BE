package com.ktb10.kgb.content.service;

/** 가이드북 일정에 영향을 주는 행사 변경 이력입니다. */
public record GuidebookEventChangeCandidate(
        Long occurrenceId,
        String sourceContentId,
        boolean cancelled) {
}
