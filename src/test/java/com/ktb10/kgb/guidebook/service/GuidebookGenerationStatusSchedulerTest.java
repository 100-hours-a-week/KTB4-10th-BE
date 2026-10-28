package com.ktb10.kgb.guidebook.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class GuidebookGenerationStatusSchedulerTest {

    @Mock
    private GenerationJobRepository generationJobRepository;
    @Mock
    private GuidebookGenerationStatusSynchronizer synchronizer;

    @Test
    void synchronizesNextBatchAndWrapsAfterLastJob() {
        given(generationJobRepository.findSynchronizableIdsAfter(any(), anyLong(), any(Pageable.class)))
                .willReturn(List.of(11L, 12L))
                .willReturn(List.of())
                .willReturn(List.of(3L));
        GuidebookGenerationStatusScheduler scheduler =
                new GuidebookGenerationStatusScheduler(generationJobRepository, synchronizer, 2);

        scheduler.synchronizeActiveJobs();
        scheduler.synchronizeActiveJobs();

        InOrder order = inOrder(synchronizer);
        order.verify(synchronizer).synchronize(11L);
        order.verify(synchronizer).synchronize(12L);
        order.verify(synchronizer).synchronize(3L);
    }
}
