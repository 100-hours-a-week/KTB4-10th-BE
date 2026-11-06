package com.ktb10.kgb.guidebook.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ktb10.kgb.guidebook.client.AiGuidebookRequestMapper;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.JobType;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    public GenerationMessagePreparation(AiGuidebookRequestMapper mapper, ObjectMapper json, Clock clock) {
        this.mapper = mapper;
        this.json = json;
        this.clock = clock;
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
            job.preparePublication(json.writeValueAsString(snapshot), LocalDateTime.now(clock));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("생성 메시지 스냅샷을 구성할 수 없습니다.", exception);
        }
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
