package com.ktb10.kgb.member.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.member.dto.response.NotificationListResponse;
import com.ktb10.kgb.member.service.NotificationService;
import com.ktb10.kgb.member.service.NotificationStreamService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 인증 회원의 미읽음 인앱 알림 API를 제공합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final String LIST_SUCCESS_MESSAGE = "notification_list_success";

    private final NotificationService notificationService;
    private final NotificationStreamService notificationStreamService;

    public NotificationController(
            NotificationService notificationService,
            NotificationStreamService notificationStreamService) {
        this.notificationService = notificationService;
        this.notificationStreamService = notificationStreamService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamNotifications(
            @AuthenticationPrincipal AuthenticatedMember member) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.VARY, HttpHeaders.ORIGIN)
                .header("X-Accel-Buffering", "no")
                .body(notificationStreamService.subscribe(member.memberId(), member.sessionId()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> getNotifications(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "4") @Min(1) @Max(20) int size) {
        NotificationListResponse response = notificationService.getNotifications(
                member.memberId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(LIST_SUCCESS_MESSAGE, response));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable @Positive Long notificationId) {
        notificationService.deleteNotification(member.memberId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAllNotifications(
            @AuthenticationPrincipal AuthenticatedMember member) {
        notificationService.deleteAllNotifications(member.memberId());
        return ResponseEntity.noContent().build();
    }
}
