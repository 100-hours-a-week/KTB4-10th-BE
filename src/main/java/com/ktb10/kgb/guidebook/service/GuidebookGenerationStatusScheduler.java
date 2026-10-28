package com.ktb10.kgb.guidebook.service;

import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 프론트 폴링 없이 AI 생성 작업 상태를 주기적으로 동기화합니다. */
@Component
@ConditionalOnProperty(name = "ai.generation.delivery-mode", havingValue = "polling", matchIfMissing = true)
public class GuidebookGenerationStatusScheduler {

    private static final Set<GenerationStatus> ACTIVE_STATUSES = EnumSet.of(
            GenerationStatus.PENDING,
            GenerationStatus.PROCESSING);

    private final GenerationJobRepository generationJobRepository;
    private final GuidebookGenerationStatusSynchronizer synchronizer;
    private final int batchSize;
    private long lastJobId;

    public GuidebookGenerationStatusScheduler(
            GenerationJobRepository generationJobRepository,
            GuidebookGenerationStatusSynchronizer synchronizer,
            @Value("${guidebook.generation.status-sync.batch-size:50}") int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("가이드북 상태 동기화 배치 크기는 0보다 커야 합니다.");
        }
        this.generationJobRepository = generationJobRepository;
        this.synchronizer = synchronizer;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString =
            "${guidebook.generation.status-sync.interval-millis:5000}")
    public void synchronizeActiveJobs() {
        List<Long> jobIds = findNextBatch(lastJobId);
        if (jobIds.isEmpty() && lastJobId > 0) {
            lastJobId = 0;
            jobIds = findNextBatch(lastJobId);
        }
        if (!jobIds.isEmpty()) {
            lastJobId = jobIds.get(jobIds.size() - 1);
        }
        jobIds.forEach(synchronizer::synchronize);
    }

    private List<Long> findNextBatch(long afterId) {
        return generationJobRepository.findSynchronizableIdsAfter(
                ACTIVE_STATUSES,
                afterId,
                PageRequest.of(0, batchSize));
    }
}
