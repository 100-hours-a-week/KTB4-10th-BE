package com.ktb10.kgb.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/** 원문 서비스 세션 ID를 DB 조회용 SHA-256 해시로 변환합니다. */
@Component
public class SessionIdHasher {

    public byte[] hash(String rawSessionId) {
        if (rawSessionId == null || rawSessionId.isBlank()) {
            throw new IllegalArgumentException("세션 ID는 비어 있을 수 없습니다.");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(rawSessionId.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 해시 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
