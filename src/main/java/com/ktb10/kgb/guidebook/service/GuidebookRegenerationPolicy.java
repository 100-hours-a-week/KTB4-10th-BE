package com.ktb10.kgb.guidebook.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/** 최초 생성 완료 시각과 성공 버전을 기준으로 재생성 가능 상태를 계산합니다. */
public final class GuidebookRegenerationPolicy {

    public static final int MAX_REGENERATION_COUNT = 3;
    public static final Duration REGENERATION_WINDOW = Duration.ofMinutes(10);

    private GuidebookRegenerationPolicy() {
    }

    public static RegenerationAvailability evaluate(
            int guidebookVersion,
            LocalDateTime initialCompletedAt,
            LocalDateTime now) {
        if (guidebookVersion < 1) {
            throw new IllegalArgumentException("가이드북 버전은 1 이상이어야 합니다.");
        }
        LocalDateTime completedAt = Objects.requireNonNull(initialCompletedAt);
        LocalDateTime evaluatedAt = Objects.requireNonNull(now);
        int successfulRegenerationCount = guidebookVersion - 1;
        int remainingCount = Math.max(
                0,
                MAX_REGENERATION_COUNT - successfulRegenerationCount);
        LocalDateTime expiresAt = completedAt.plus(REGENERATION_WINDOW);
        boolean available = remainingCount > 0 && evaluatedAt.isBefore(expiresAt);
        return new RegenerationAvailability(remainingCount, expiresAt, available);
    }

    public record RegenerationAvailability(
            int remainingCount,
            LocalDateTime expiresAt,
            boolean available) {

        public static RegenerationAvailability unavailable() {
            return new RegenerationAvailability(0, null, false);
        }
    }
}
