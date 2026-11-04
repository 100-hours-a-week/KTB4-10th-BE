package com.ktb10.kgb.content.repository;

/** 변경된 행사를 포함한 생성자 소유 가이드북과 수신 회원입니다. */
public record GuidebookEventChangeNotificationTarget(
        Long memberId,
        Long guidebookId,
        String sourceContentId) {
}
