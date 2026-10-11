package com.ktb10.kgb.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.member.repository.MemberRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MonthlyFreeCreditRefillSchedulerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-10-01T00:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MonthlyFreeCreditRefillService refillService;

    private SimpleMeterRegistry meterRegistry;
    private MonthlyFreeCreditRefillScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new MonthlyFreeCreditRefillScheduler(
                memberRepository,
                refillService,
                FIXED_CLOCK,
                meterRegistry);
    }

    @Test
    void continuesProcessingAndRecordsFailureMetricWhenOneMemberFails() {
        PageRequest firstPage = PageRequest.of(0, 100);
        when(memberRepository.findNonDeletedMemberIdsAfter(0L, firstPage))
                .thenReturn(List.of(1L, 2L));
        when(memberRepository.findNonDeletedMemberIdsAfter(2L, firstPage))
                .thenReturn(List.of());
        when(refillService.refill(1L, YearMonth.of(2026, 10)))
                .thenThrow(new IllegalStateException("테스트 실패"));
        when(refillService.refill(2L, YearMonth.of(2026, 10)))
                .thenReturn(MonthlyFreeCreditRefillResult.REFILLED);

        scheduler.reconcileCurrentMonth();

        verify(refillService).refill(2L, YearMonth.of(2026, 10));
        assertThat(meterRegistry.get("kgb.credit.monthly_free_refill.failures")
                .counter()
                .count()).isEqualTo(1.0d);
    }
}
