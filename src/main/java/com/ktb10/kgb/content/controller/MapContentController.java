package com.ktb10.kgb.content.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.content.dto.MapContentResponse;
import com.ktb10.kgb.content.service.MapContentService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 카카오맵 화면 영역에 표시할 관광 콘텐츠를 제공합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/map/contents")
public class MapContentController {

    private static final String SUCCESS_MESSAGE = "map_content_success";

    private final MapContentService mapContentService;

    public MapContentController(MapContentService mapContentService) {
        this.mapContentService = mapContentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MapContentResponse>> getContents(
            @RequestParam double south,
            @RequestParam double west,
            @RequestParam double north,
            @RequestParam double east,
            @RequestParam @Min(6) @Max(21) int zoom,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit) {
        MapContentResponse response = mapContentService.getContents(
                south, west, north, east, limit);
        return ResponseEntity.ok(ApiResponse.success(SUCCESS_MESSAGE, response));
    }
}

