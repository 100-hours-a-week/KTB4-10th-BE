package com.ktb10.kgb.guidebook.callback;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationTimeoutRecoveryTest {

    @Test
    void restoresRemainingOrExpiredTimerFromAttemptStart() {
        GenerationJobRepository jobs = mock(GenerationJobRepository.class);
        GenerationTimeoutRegistry timers = mock(GenerationTimeoutRegistry.class);
        GenerationCallbackSettings settings = mock(GenerationCallbackSettings.class);
        GenerationJob job = mock(GenerationJob.class);
        LocalDateTime startedAt = LocalDateTime.of(2026, 11, 5, 9, 0);
        when(job.getId()).thenReturn(323L);
        when(job.getAttemptCount()).thenReturn((short) 1);
        when(job.getAttemptStartedAt()).thenReturn(startedAt);
        when(jobs.findAllByStatusInAndAttemptStartedAtIsNotNull(anyCollection()))
                .thenReturn(List.of(job));
        when(settings.timeout()).thenReturn(Duration.ofMinutes(15));
        var recovery = new GenerationTimeoutRecovery(jobs, timers, settings);

        recovery.restore();

        verify(timers).register(
                323L,
                1,
                Instant.ofEpochSecond(startedAt.toEpochSecond(ZoneOffset.UTC))
                        .plus(Duration.ofMinutes(15)));
    }
}
