package com.ktb10.kgb.member.service;

import com.ktb10.kgb.member.event.NotificationCreatedEvent;

/** 알림과 연결 종료 이벤트를 현재 배포 형태에 맞는 실시간 전달 통로로 발행합니다. */
public interface NotificationRealtimePublisher {

    void publishNotification(NotificationCreatedEvent event);

    void publishSessionDisconnected(Long memberId, String sessionHash);

    void publishMemberDisconnected(Long memberId);
}
