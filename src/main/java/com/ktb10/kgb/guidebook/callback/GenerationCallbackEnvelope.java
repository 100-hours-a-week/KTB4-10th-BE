package com.ktb10.kgb.guidebook.callback;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.guidebook.client.AiGenerationStatus;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 기존 AI 응답 envelope에 콜백 식별자와 진행 정보를 추가합니다. */
public record GenerationCallbackEnvelope(
        @NotBlank String message,
        @NotNull @Valid Data data) {

    @AssertTrue(message = "콜백 message와 status가 일치해야 합니다.")
    @JsonIgnore
    public boolean isMessageConsistent() {
        return data == null || data.status() == null
                || ("guidebook_" + data.status().value()).equals(message);
    }

    public record Data(
            @JsonProperty("event_id") @NotNull UUID eventId,
            @JsonProperty("request_id") @NotBlank @Pattern(regexp = "[1-9][0-9]{0,17}-[0-3]")
            String requestId,
            @Positive long sequence,
            @JsonProperty("job_id") @NotBlank @Size(max = 255) String jobId,
            @NotNull AiGenerationStatus status,
            @Valid Progress progress,
            AiGenerationStatusResponse.GuidebookResult result,
            AiGenerationStatusResponse.GenerationError error) {

        @AssertTrue(message = "상태에 맞는 progress, result, error를 전달해야 합니다.")
        @JsonIgnore
        public boolean isPayloadConsistent() {
            if (status == null) {
                return true;
            }
            return switch (status) {
                case PENDING -> progress != null && "queued".equals(progress.step())
                        && result == null && error == null;
                case PROCESSING -> progress != null && !"queued".equals(progress.step())
                        && result == null && error == null;
                case COMPLETED -> progress == null && result != null && error == null
                        && result.title() != null && !result.title().isBlank()
                        && result.contentHtml() != null && !result.contentHtml().isBlank();
                case FAILED -> progress == null && result == null && error != null
                        && error.code() != null && !error.code().isBlank()
                        && error.code().length() <= 80
                        && error.message() != null && !error.message().isBlank()
                        && error.message().length() <= 500;
            };
        }

        public long backendJobId() {
            return Long.parseLong(requestId.substring(0, requestId.indexOf('-')));
        }

        public int attempt() {
            return Integer.parseInt(requestId.substring(requestId.indexOf('-') + 1));
        }

        public AiGenerationStatusResponse response() {
            return new AiGenerationStatusResponse(jobId, status, result, error);
        }
    }

    public record Progress(
            @NotBlank @Pattern(regexp = "queued|arranging|writing|rendering") String step,
            @NotBlank @Size(max = 200) String message) {
    }
}
