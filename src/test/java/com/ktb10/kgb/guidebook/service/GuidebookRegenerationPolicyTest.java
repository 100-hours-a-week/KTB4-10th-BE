package com.ktb10.kgb.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GuidebookRegenerationPolicyTest {

    private static final LocalDateTime COMPLETED_AT =
            LocalDateTime.of(2026, 10, 23, 9, 0);

    @Test
    void allowsThreeRegenerationsForInitialVersionWithinTenMinutes() {
        var availability = GuidebookRegenerationPolicy.evaluate(
                1,
                COMPLETED_AT,
                COMPLETED_AT.plusMinutes(9).plusSeconds(59));

        assertThat(availability.remainingCount()).isEqualTo(3);
        assertThat(availability.expiresAt()).isEqualTo(COMPLETED_AT.plusMinutes(10));
        assertThat(availability.available()).isTrue();
    }

    @Test
    void decreasesRemainingCountOnlyWhenGuidebookVersionIncreases() {
        var availability = GuidebookRegenerationPolicy.evaluate(
                3,
                COMPLETED_AT,
                COMPLETED_AT.plusMinutes(1));

        assertThat(availability.remainingCount()).isEqualTo(1);
        assertThat(availability.available()).isTrue();
    }

    @Test
    void rejectsAtExactTenMinuteBoundary() {
        var availability = GuidebookRegenerationPolicy.evaluate(
                1,
                COMPLETED_AT,
                COMPLETED_AT.plusMinutes(10));

        assertThat(availability.remainingCount()).isEqualTo(3);
        assertThat(availability.available()).isFalse();
    }

    @Test
    void rejectsAfterThreeSuccessfulRegenerations() {
        var availability = GuidebookRegenerationPolicy.evaluate(
                4,
                COMPLETED_AT,
                COMPLETED_AT.plusMinutes(1));

        assertThat(availability.remainingCount()).isZero();
        assertThat(availability.available()).isFalse();
    }
}
