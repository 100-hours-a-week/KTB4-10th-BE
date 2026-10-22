package com.ktb10.kgb.content.tourapi;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class TourApiDetailBackfillRunnerTest {

    @Test
    void executesConfiguredNumberOfBatchesUsingReturnedCursor() throws Exception {
        TourApiDetailBackfillService service = mock(TourApiDetailBackfillService.class);
        TourApiDetailBackfillProperties properties = new TourApiDetailBackfillProperties(
                true,
                100,
                2,
                0L,
                Duration.ofMillis(100));
        when(service.runBatch(0L, 100, Duration.ofMillis(100)))
                .thenReturn(new TourApiDetailBackfillService.BackfillBatchResult(
                        0L, 150L, 100, 99, List.of("failed-1"), true));
        when(service.runBatch(150L, 100, Duration.ofMillis(100)))
                .thenReturn(new TourApiDetailBackfillService.BackfillBatchResult(
                        150L, 270L, 80, 80, List.of(), false));

        TourApiDetailBackfillRunner runner =
                new TourApiDetailBackfillRunner(service, properties);
        runner.run(new DefaultApplicationArguments());

        verify(service).runBatch(0L, 100, Duration.ofMillis(100));
        verify(service).runBatch(150L, 100, Duration.ofMillis(100));
    }
}
