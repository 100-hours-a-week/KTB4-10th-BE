package com.ktb10.kgb.content.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.content.dto.NewEventListResponse;
import com.ktb10.kgb.content.service.NewEventService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 일자별 TourAPI 신규 행사 목록 API를 제공합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/events")
public class NewEventController {

    private static final String LIST_SUCCESS_MESSAGE = "new_event_list_success";

    private final NewEventService newEventService;

    public NewEventController(NewEventService newEventService) {
        this.newEventService = newEventService;
    }

    @GetMapping("/new")
    public ResponseEntity<ApiResponse<NewEventListResponse>> getNewEvents(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(ApiResponse.success(
                LIST_SUCCESS_MESSAGE,
                newEventService.getNewEvents(date, page, size)));
    }
}
