package com.ktb10.kgb.member.service;

/** 브라우저 Push 서비스로 암호화된 payload를 보내는 경계입니다. */
public interface WebPushGateway {

    boolean isConfigured();

    WebPushSendResult send(WebPushDeliveryTarget target, byte[] payload, String topic);
}
