package com.ktb10.kgb.guidebook.event;

import static org.mockito.Mockito.verify;

import com.ktb10.kgb.guidebook.service.GuidebookAiTriggerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuidebookGenerationEventListenerTest {

    @Mock
    private GuidebookAiTriggerService guidebookAiTriggerService;

    @Test
    void triggersAiGenerationForCommittedJob() {
        GuidebookGenerationEventListener listener =
                new GuidebookGenerationEventListener(guidebookAiTriggerService);

        listener.handle(new GuidebookGenerationRequestedEvent(301L));

        verify(guidebookAiTriggerService).trigger(301L);
    }
}
