package com.ktb10.kgb.common.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class KakaoOauthUserMapperTest {

    private final KakaoOauthUserMapper mapper = new KakaoOauthUserMapper();

    @Test
    void mapsNestedKakaoAccountProfileAndNullableEmail() {
        KakaoOauthUser result = mapper.map(Map.of(
                "id", 123456789L,
                "kakao_account", Map.of(
                        "profile", Map.of(
                                "nickname", "여행자",
                                "profile_image_url", "https://example.com/profile.png"))));

        assertThat(result).isEqualTo(new KakaoOauthUser(
                "123456789",
                "여행자",
                null,
                "https://example.com/profile.png"));
    }
}
