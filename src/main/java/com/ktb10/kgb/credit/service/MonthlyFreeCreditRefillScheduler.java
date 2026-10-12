package com.ktb10.kgb.credit.service;

import com.ktb10.kgb.member.repository.MemberRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매월 1일 회원별 무료 생성권을 보충하고 실패를 독립적으로 관측합니다. */
@Component
public class MonthlyFreeCreditRefillScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            MonthlyFreeCreditRefillScheduler.class);
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int BATCH_SIZE = 100;

    private final MemberRepository memberRepository;
    private final MonthlyFreeCreditRefillService refillService;
    private final Clock clock;
    private final Counter failureCounter;

    public MonthlyFreeCreditRefillScheduler(
            MemberRepository memberRepository,
            MonthlyFreeCreditRefillService refillService,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.memberRepository = memberRepository;
        this.refillService = refillService;
        this.clock = clock;
        this.failureCounter = Counter.builder("kgb.credit.monthly_free_refill.failures")
                .description("월 무료 생성권 보충에 실패한 회원 수")
                .register(meterRegistry);
    }

    @Scheduled(
            cron = "${credit.monthly-free-reconciliation-cron:0 0 0 1 * *}",
            zone = "Asia/Seoul")
    public void reconcileCurrentMonth() {
        YearMonth targetMonth = YearMonth.from(clock.instant().atZone(SEOUL_ZONE));
        Map<MonthlyFreeCreditRefillResult, Integer> counts =
                new EnumMap<>(MonthlyFreeCreditRefillResult.class);
        int failures = 0;
        long lastMemberId = 0L;

        while (true) {
            List<Long> memberIds = memberRepository.findNonDeletedMemberIdsAfter(
                    lastMemberId,
                    PageRequest.of(0, BATCH_SIZE));
            if (memberIds.isEmpty()) {
                break;
            }
            for (Long memberId : memberIds) {
                try {
                    MonthlyFreeCreditRefillResult result = refillService.refill(
                            memberId,
                            targetMonth);
                    counts.merge(result, 1, Integer::sum);
                } catch (RuntimeException exception) {
                    failures++;
                    failureCounter.increment();
                    LOGGER.error(
                            "월 무료 생성권 재대사에 실패했습니다. memberId={}, targetMonth={}",
                            memberId,
                            targetMonth,
                            exception);
                }
            }
            lastMemberId = memberIds.get(memberIds.size() - 1);
        }

        LOGGER.info(
                "월 무료 생성권 재대사를 완료했습니다. targetMonth={}, refilled={}, "
                        + "noChange={}, alreadyProcessed={}, ineligible={}, failures={}",
                targetMonth,
                count(counts, MonthlyFreeCreditRefillResult.REFILLED),
                count(counts, MonthlyFreeCreditRefillResult.NO_BALANCE_CHANGE),
                count(counts, MonthlyFreeCreditRefillResult.ALREADY_PROCESSED),
                count(counts, MonthlyFreeCreditRefillResult.MEMBER_NOT_ELIGIBLE),
                failures);
    }

    private static int count(
            Map<MonthlyFreeCreditRefillResult, Integer> counts,
            MonthlyFreeCreditRefillResult result) {
        return counts.getOrDefault(result, 0);
    }
}
