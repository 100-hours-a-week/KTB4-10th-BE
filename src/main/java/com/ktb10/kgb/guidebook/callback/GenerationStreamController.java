package com.ktb10.kgb.guidebook.callback;

import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.guidebook.service.GuidebookGenerationService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 본인 작업의 상태만 알림 설정과 무관하게 구독합니다. */
@RestController
@Validated
public class GenerationStreamController {

    private final GuidebookGenerationService generations;
    private final GenerationStreamService streams;

    public GenerationStreamController(
            GuidebookGenerationService generations, GenerationStreamService streams) {
        this.generations = generations;
        this.streams = streams;
    }

    @GetMapping(value = "/api/v1/guidebook-generations/{jobId}/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable @Positive Long jobId) {
        // 응답이 열리기 전에 소유권을 확인하고 재연결 snapshot은 등록 후 다시 조회합니다.
        generations.getGenerationJobStatus(member.memberId(), jobId);
        SseEmitter emitter = streams.subscribe(
                member.memberId(), member.sessionHash(), jobId,
                () -> generations.getGenerationJobStatus(member.memberId(), jobId));
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }
}
