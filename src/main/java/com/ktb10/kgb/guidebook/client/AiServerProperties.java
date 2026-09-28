package com.ktb10.kgb.guidebook.client;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 실제 AI 서버 접속에 사용하는 설정입니다. */
@ConfigurationProperties(prefix = "ai.server")
public record AiServerProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout) {
}
