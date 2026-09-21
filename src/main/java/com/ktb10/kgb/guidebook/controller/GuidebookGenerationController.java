package com.ktb10.kgb.guidebook.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.response.GenerationStatusResponse;
import com.ktb10.kgb.guidebook.dto.response.GuidebookGenerationResponse;
import com.ktb10.kgb.guidebook.service.GuidebookGenerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 가이드북 생성 작업 API를 제공합니다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/guidebook-generations")
public class GuidebookGenerationController {

    private static final String ACCEPTED_MESSAGE = "guidebook_generation_accepted";

    private final GuidebookGenerationService guidebookGenerationService;

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<GenerationStatusResponse>> getGenerationJobStatus(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable @Positive Long jobId) {
        GenerationStatusResponse response =
                guidebookGenerationService.getGenerationJobStatus(member.memberId(), jobId);

        return ResponseEntity.ok(
                ApiResponse.success("generation_job_get_success", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GuidebookGenerationResponse>> createInitial(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = "[\\x20-\\x7E]{1,100}")
            String idempotencyKey,
            @Valid @RequestBody GuidebookGenerationRequest request) {
        GuidebookGenerationResponse response = guidebookGenerationService.createInitial(
                member.memberId(),
                idempotencyKey,
                request);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(ACCEPTED_MESSAGE, response));
    }
}
