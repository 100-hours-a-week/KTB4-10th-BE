package com.ktb10.kgb.guidebook.callback;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** DB 처리 커밋이 끝난 뒤에만 콜백을 승인합니다. */
@RestController
@ConditionalOnProperty(name = "ai.generation.delivery-mode", havingValue = "callback")
public class GenerationCallbackController {

    private final GenerationCallbackTransactionService transactions;

    public GenerationCallbackController(GenerationCallbackTransactionService transactions) {
        this.transactions = transactions;
    }

    @PostMapping("/internal/v1/ai/generation-events")
    public ResponseEntity<Void> receive(@Valid @RequestBody GenerationCallbackEnvelope envelope) {
        transactions.receive(envelope);
        return ResponseEntity.noContent().build();
    }
}
