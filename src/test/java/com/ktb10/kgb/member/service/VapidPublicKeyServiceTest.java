package com.ktb10.kgb.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.member.error.MemberErrorCode;
import org.junit.jupiter.api.Test;

class VapidPublicKeyServiceTest {

    private static final String VALID_PUBLIC_KEY =
            "BAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Test
    void returnsConfiguredP256PublicKey() {
        VapidPublicKeyService service = new VapidPublicKeyService(VALID_PUBLIC_KEY);

        assertThat(service.getPublicKey().publicKey()).isEqualTo(VALID_PUBLIC_KEY);
    }

    @Test
    void rejectsMissingAndMalformedPublicKey() {
        assertThatThrownBy(() -> new VapidPublicKeyService("").getPublicKey())
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                        exception.errorCode()).isEqualTo(
                                MemberErrorCode.WEB_PUSH_CONFIGURATION_UNAVAILABLE));
        assertThatThrownBy(() -> new VapidPublicKeyService("not-a-p256-key").getPublicKey())
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                        exception.errorCode()).isEqualTo(
                                MemberErrorCode.WEB_PUSH_CONFIGURATION_UNAVAILABLE));
    }
}
