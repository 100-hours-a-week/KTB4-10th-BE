package com.ktb10.kgb.guidebook.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.guidebook.client.AiGuidebookRequestMapper;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.dto.request.RegenerationRequestPayload;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.JobType;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 발행하지 않고 접수 트랜잭션 안에서 전체 메시지만 준비합니다. */
@Component
@ConditionalOnProperty(name = "ai.generation.outbox-enabled", havingValue = "true")
public class GenerationMessagePreparation {

    private final AiGuidebookRequestMapper mapper;
    private final ObjectMapper json;
    private final Clock clock;
    private final RegenerationInputSnapshot regeneration;
    private final ApplicationEventPublisher events;

    public GenerationMessagePreparation(AiGuidebookRequestMapper mapper, ObjectMapper json, Clock clock,
            RegenerationInputSnapshot regeneration, ApplicationEventPublisher events) {
        this.mapper = mapper;
        this.json = json;
        this.clock = clock;
        this.regeneration = regeneration;
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void prepareRegeneration(GenerationJob job, GenerationJob source, Guidebook guidebook) {
        if (job.getJobType() != JobType.REGENERATION) {
            throw new IllegalArgumentException("재생성 작업만 지원합니다.");
        }
        try {
            var request = json.readValue(job.getRequestPayload(), RegenerationRequestPayload.class);
            ObjectNode snapshot = json.createObjectNode();
            snapshot.put("schemaVersion", 1);
            snapshot.set("input", regeneration.build(source, guidebook, request.request().feedback()));
            preparePublication(job, json.writeValueAsString(snapshot));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("재생성 메시지를 구성할 수 없습니다.", exception);
        }
    }

    /** 사용자 재시도도 최초 접수 때의 원본 일정과 후보를 유지합니다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void prepareRegenerationRetry(GenerationJob job, String previousSnapshot) {
        if (job.getJobType() != JobType.REGENERATION) {
            throw new IllegalArgumentException("재생성 작업만 지원합니다.");
        }
        if (previousSnapshot == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_STATE_CONFLICT);
        }
        preparePublication(job, previousSnapshot);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void prepareInitial(GenerationJob job) {
        if (job.getJobType() != JobType.INITIAL) {
            throw new IllegalArgumentException("최초 생성 메시지만 지원합니다.");
        }
        try {
            InitialGenerationRequestPayload input = json.readValue(
                    job.getRequestPayload(), InitialGenerationRequestPayload.class);
            AiGuidebookRequest request = mapper.mapInput(input);
            ObjectNode inputSnapshot = json.valueToTree(request);
            inputSnapshot.remove("request_id");
            ObjectNode snapshot = json.createObjectNode();
            snapshot.put("schemaVersion", 1);
            snapshot.set("input", inputSnapshot);
            preparePublication(job, json.writeValueAsString(snapshot));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("생성 메시지 스냅샷을 구성할 수 없습니다.", exception);
        }
    }

    private void preparePublication(GenerationJob job, String snapshot) {
        job.preparePublication(snapshot, LocalDateTime.now(clock));
        events.publishEvent(new PublicationReady());
    }

    record PublicationReady() {
    }

    /** 발행 시 DB 식별자를 붙이며 저장된 입력이나 최신 콘텐츠는 변경/재조회하지 않습니다. */
    public String buildMessage(GenerationJob job) {
        if (job.getMessagePayload() == null) {
            throw new IllegalStateException("저장된 입력 스냅샷이 없습니다.");
        }
        try {
            ObjectNode message = (ObjectNode) json.readTree(job.getMessagePayload());
            message.put("messageId", job.getMessageId());
            message.put("jobId", job.getId());
            message.put("attempt", job.getAttemptCount().intValue());
            message.put("jobType", job.getJobType().name());
            ((ObjectNode) message.get("input")).put("request_id", job.getMessageId());
            return json.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 입력 스냅샷을 읽을 수 없습니다.", exception);
        }
    }
}
