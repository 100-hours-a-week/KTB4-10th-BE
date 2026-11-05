package com.ktb10.kgb.guidebook.callback;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** DB 처리 커밋이 끝난 뒤에만 콜백을 승인합니다. */
@RestController
public class GenerationCallbackController {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationCallbackController.class);
    private final GenerationCallbackTransactionService transactions;

    public GenerationCallbackController(GenerationCallbackTransactionService transactions) {
        this.transactions = transactions;
    }

    @PostMapping("/internal/v1/ai/generation-events")
    public ResponseEntity<Void> receive(@Valid @RequestBody GenerationCallbackEnvelope envelope) {
        GenerationCallbackTransactionService.CallbackHandlingResult result =
                transactions.receive(envelope);
        if (result.handled()) {
            GenerationCallbackEnvelope.Data data = envelope.data();
            LOGGER.atInfo()
                    .addKeyValue("event", "ai_generation_callback_applied")
                    .addKeyValue("backendJobId", data.backendJobId())
                    .addKeyValue("requestId", data.requestId())
                    .addKeyValue("aiJobId", data.jobId())
                    .addKeyValue("sequence", data.sequence())
                    .addKeyValue("receivedStatus", data.status())
                    .addKeyValue("status", result.status())
                    .addKeyValue("errorCode", result.errorCode())
                    .log("AI 생성 콜백을 반영했습니다.");
        }
        return ResponseEntity.noContent().build();
    }
}
