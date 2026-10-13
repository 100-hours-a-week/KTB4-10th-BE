package com.ktb10.kgb.member.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.member.dto.response.VapidPublicKeyResponse;
import com.ktb10.kgb.member.error.MemberErrorCode;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 환경 변수로 주입된 VAPID 공개키를 브라우저 구독 생성에 제공합니다. */
@Service
public class VapidPublicKeyService {

    private static final int UNCOMPRESSED_P256_PUBLIC_KEY_BYTES = 65;
    private static final byte UNCOMPRESSED_POINT_PREFIX = 0x04;

    private final String publicKey;

    public VapidPublicKeyService(
            @Value("${webpush.vapid.public-key:}") String publicKey) {
        this.publicKey = publicKey == null ? "" : publicKey.trim();
    }

    public VapidPublicKeyResponse getPublicKey() {
        if (!isValidPublicKey(publicKey)) {
            throw new BusinessException(MemberErrorCode.WEB_PUSH_CONFIGURATION_UNAVAILABLE);
        }
        return new VapidPublicKeyResponse(publicKey);
    }

    private boolean isValidPublicKey(String value) {
        if (value.isBlank()) {
            return false;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return decoded.length == UNCOMPRESSED_P256_PUBLIC_KEY_BYTES
                    && decoded[0] == UNCOMPRESSED_POINT_PREFIX;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
