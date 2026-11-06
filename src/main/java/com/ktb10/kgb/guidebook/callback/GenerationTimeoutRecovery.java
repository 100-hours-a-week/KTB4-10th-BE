package com.ktb10.kgb.guidebook.callback;

import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 재시작으로 사라진 진행 중 생성 작업의 남은 제한 시간 타이머를 복구합니다. */
@Component
public class GenerationTimeoutRecovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationTimeoutRecovery.class);
    private static final List<GenerationStatus> ACTIVE_STATUSES = List.of(
            GenerationStatus.PENDING,
            GenerationStatus.PROCESSING);

    private final GenerationJobRepository jobs;
    private final GenerationTimeoutRegistry timers;
    private final GenerationCallbackSettings settings;

    public GenerationTimeoutRecovery(
            GenerationJobRepository jobs,
            GenerationTimeoutRegistry timers,
            GenerationCallbackSettings settings) {
        this.jobs = jobs;
        this.timers = timers;
        this.settings = settings;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void restore() {
        var activeJobs = jobs.findAllByStatusInAndAttemptStartedAtIsNotNull(ACTIVE_STATUSES);
        activeJobs.forEach(job -> {
            Instant deadline = job.getAttemptStartedAt()
                    .toInstant(ZoneOffset.UTC)
                    .plus(settings.timeout());
            timers.register(job.getId(), job.getAttemptCount(), deadline);
        });
        if (!activeJobs.isEmpty()) {
            LOGGER.info("재시작 후 생성 타임아웃을 복구했습니다. count={}", activeJobs.size());
        }
    }
}
