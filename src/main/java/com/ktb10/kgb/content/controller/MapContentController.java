package com.ktb10.kgb.content.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.content.dto.MapContentListResponse;
import com.ktb10.kgb.content.dto.MapMarkerResponse;
import com.ktb10.kgb.content.service.MapContentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 카카오맵 화면 영역에 표시할 관광 콘텐츠를 제공합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/map")
public class MapContentController {

    private static final String MARKER_SUCCESS_MESSAGE = "map_marker_success";
    private static final String CONTENT_SUCCESS_MESSAGE = "map_content_success";

    private final MapContentService mapContentService;

    public MapContentController(MapContentService mapContentService) {
        this.mapContentService = mapContentService;
    }

    @GetMapping("/markers")
    public ResponseEntity<ApiResponse<MapMarkerResponse>> getMarkers(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestParam double south,
            @RequestParam double west,
            @RequestParam double north,
            @RequestParam double east,
            @RequestParam int zoom) {
        MapMarkerResponse response = mapContentService.getMarkers(
                member.memberId(), south, west, north, east, zoom);
        return ResponseEntity.ok(ApiResponse.success(MARKER_SUCCESS_MESSAGE, response));
    }

    @GetMapping("/contents")
    public ResponseEntity<ApiResponse<MapContentListResponse>> getContents(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestParam double south,
            @RequestParam double west,
            @RequestParam double north,
            @RequestParam double east,
            @RequestParam(required = false) String cursor) {
        MapContentListResponse response = mapContentService.getBottomSheetContents(
                member.memberId(), south, west, north, east, cursor);
        return ResponseEntity.ok(ApiResponse.success(CONTENT_SUCCESS_MESSAGE, response));
    }
}
