package com.ktb10.kgb.member.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/** 민감한 Push endpoint 원문을 노출하지 않고 중복 비교할 SHA-256 해시를 만듭니다. */
@Component
public class WebPushEndpointHasher {

    public byte[] hash(String endpoint) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(endpoint.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
