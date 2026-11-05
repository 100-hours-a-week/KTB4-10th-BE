package com.ktb10.kgb.guidebook.callback;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.guidebook.client.AiGenerationStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GenerationCallbackEnvelopeTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsCheckingProgressStep() {
        GenerationCallbackEnvelope envelope = processing("checking");

        assertThat(VALIDATOR.validate(envelope)).isEmpty();
    }

    @Test
    void rejectsUnknownProgressStep() {
        GenerationCallbackEnvelope envelope = processing("unknown");

        assertThat(VALIDATOR.validate(envelope))
                .anyMatch(violation -> violation.getPropertyPath().toString()
                        .equals("data.progress.step"));
    }

    private GenerationCallbackEnvelope processing(String step) {
        return new GenerationCallbackEnvelope(
                "guidebook_processing",
                new GenerationCallbackEnvelope.Data(
                        UUID.randomUUID(),
                        "301-0",
                        1,
                        "ai-job-301",
                        AiGenerationStatus.PROCESSING,
                        new GenerationCallbackEnvelope.Progress(step, "진행 중"),
                        null,
                        null));
    }
}
