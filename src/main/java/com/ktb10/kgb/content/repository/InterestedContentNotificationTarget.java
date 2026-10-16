package com.ktb10.kgb.content.repository;

/** 관심 행사 변경 알림을 생성할 회원과 행사 정보입니다. */
public record InterestedContentNotificationTarget(
        Long memberId,
        String sourceContentId,
        String contentTitle) {
}
