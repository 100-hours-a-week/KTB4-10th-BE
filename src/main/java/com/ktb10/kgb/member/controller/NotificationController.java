package com.ktb10.kgb.member.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.member.dto.response.NotificationListResponse;
import com.ktb10.kgb.member.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원의 미읽음 인앱 알림 API를 제공합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final String LIST_SUCCESS_MESSAGE = "notification_list_success";

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
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
