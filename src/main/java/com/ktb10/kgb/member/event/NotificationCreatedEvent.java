package com.ktb10.kgb.member.event;

import com.ktb10.kgb.member.dto.response.NotificationItemResponse;
import java.util.Objects;

/** DB에 커밋할 인앱 알림과 실시간 수신 회원을 전달하는 내부 이벤트입니다. */
public record NotificationCreatedEvent(
        Long recipientMemberId,
        NotificationItemResponse notification,
        boolean realtimeDeliveryEnabled) {

    public NotificationCreatedEvent {
        Objects.requireNonNull(recipientMemberId, "알림 수신 회원 ID는 null일 수 없습니다.");
        Objects.requireNonNull(notification, "알림 응답은 null일 수 없습니다.");
    }
}
