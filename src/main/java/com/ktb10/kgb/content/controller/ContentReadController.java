package com.ktb10.kgb.content.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.content.dto.ContentDetailResponse;
import com.ktb10.kgb.content.dto.ContentListResponse;
import com.ktb10.kgb.content.service.ContentReadService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 정보 탭의 관광 콘텐츠 조회 API를 제공합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/contents")
public class ContentReadController {

    private final ContentReadService contentReadService;

    public ContentReadController(ContentReadService contentReadService) {
        this.contentReadService = contentReadService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ContentListResponse>> getContents(
            @RequestParam(defaultValue = "EVENT") ContentCategory category,
            @RequestParam(required = false) @Size(max = 10) String q,
            @RequestParam(name = "region_code", required = false)
                    @Size(max = 20) String regionCode,
            @RequestParam(required = false)
                    @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String month,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) int size) {
        return ResponseEntity.ok(ApiResponse.success(
                "content_list_success",
                contentReadService.getEvents(q, regionCode, month, page, size)));
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<ApiResponse<ContentDetailResponse>> getContent(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable String contentId) {
        return ResponseEntity.ok(ApiResponse.success(
                "content_get_success",
                contentReadService.getEventDetail(member.memberId(), contentId)));
    }

    public enum ContentCategory {
        EVENT
    }
}
