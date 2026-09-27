package com.ktb10.kgb.guidebook.event;

/** 가이드북 생성 결과가 DB에 모두 저장됐음을 알리는 내부 이벤트입니다. */
public record GuidebookCompletedEvent(
        Long memberId,
        Long guidebookId,
        String guidebookTitle) {
}
