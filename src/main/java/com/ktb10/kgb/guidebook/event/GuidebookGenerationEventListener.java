package com.ktb10.kgb.guidebook.event;

import com.ktb10.kgb.guidebook.service.GuidebookAiTriggerService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 생성 작업 저장이 커밋된 뒤 AI 서버 접수를 시작합니다. */
@Component
@Profile("local")
public class GuidebookGenerationEventListener {

    private final GuidebookAiTriggerService guidebookAiTriggerService;

    public GuidebookGenerationEventListener(
            GuidebookAiTriggerService guidebookAiTriggerService) {
        this.guidebookAiTriggerService = guidebookAiTriggerService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(GuidebookGenerationRequestedEvent event) {
        guidebookAiTriggerService.trigger(event.jobId());
    }
}
