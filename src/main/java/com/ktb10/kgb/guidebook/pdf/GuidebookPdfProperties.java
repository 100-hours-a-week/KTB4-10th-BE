package com.ktb10.kgb.guidebook.pdf;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Gotenberg PDF 변환 연결과 애플리케이션 자원 보호 설정입니다. */
@ConfigurationProperties(prefix = "guidebook.pdf")
public record GuidebookPdfProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxConcurrentConversions,
        DataSize maxResultSize) {

    public GuidebookPdfProperties {
        Objects.requireNonNull(baseUrl, "PDF 변환기 주소는 null일 수 없습니다.");
        requirePositive(connectTimeout, "PDF 변환기 연결 제한 시간");
        requirePositive(readTimeout, "PDF 변환기 응답 제한 시간");
        if (maxConcurrentConversions < 1) {
            throw new IllegalArgumentException("PDF 동시 변환 한도는 1 이상이어야 합니다.");
        }
        Objects.requireNonNull(maxResultSize, "PDF 최대 결과 크기는 null일 수 없습니다.");
        if (maxResultSize.toBytes() < 1) {
            throw new IllegalArgumentException("PDF 최대 결과 크기는 1바이트 이상이어야 합니다.");
        }
    }

    private static void requirePositive(Duration value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "은 null일 수 없습니다.");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + "은 0보다 커야 합니다.");
        }
    }
}
