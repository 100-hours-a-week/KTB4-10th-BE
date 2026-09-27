package com.ktb10.kgb.guidebook.event;

/** DB에 접수된 가이드북 생성 작업을 AI 서버에 전달하기 위한 이벤트입니다. */
public record GuidebookGenerationRequestedEvent(Long jobId) {
}
