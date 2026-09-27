package com.ktb10.kgb.member.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.member.entity.Notification;
import java.util.List;
import org.springframework.data.domain.Page;

/** 미읽음 알림 번호 페이지 응답입니다. */
public record NotificationListResponse(
        List<NotificationItemResponse> items,
        int page,
        int size,
        @JsonProperty("total_items")
        long totalItems,
        @JsonProperty("total_pages")
        int totalPages,
        @JsonProperty("unread_count")
        long unreadCount) {

    public static NotificationListResponse from(Page<Notification> notifications, int page) {
        List<NotificationItemResponse> items = notifications.getContent().stream()
                .map(NotificationItemResponse::from)
                .toList();
        long totalItems = notifications.getTotalElements();
        return new NotificationListResponse(
                items,
                page,
                notifications.getSize(),
                totalItems,
                notifications.getTotalPages(),
                totalItems);
    }
}
