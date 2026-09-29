package com.ktb10.kgb.common.security.oauth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoUnlinkClientTest {

    private static final String UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";

    private MockRestServiceServer server;
    private KakaoUnlinkClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoUnlinkClient(
                builder.build(),
                "test-admin-key",
                URI.create(UNLINK_URL));
    }

    @Test
    void unlinksKakaoUserWithAdminKey() {
        server.expect(once(), requestTo(UNLINK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "KakaoAK test-admin-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("target_id_type=user_id&target_id=12345"))
                .andRespond(withSuccess("{\"id\":12345}", MediaType.APPLICATION_JSON));

        client.unlink("12345");

        server.verify();
    }

    @Test
    void convertsKakaoServerFailureToUpstreamError() {
        server.expect(requestTo(UNLINK_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.unlink("12345"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(CommonErrorCode.UPSTREAM_SERVICE_ERROR));
        server.verify();
    }

    @Test
    void rejectsMissingAdminKeyWithoutCallingKakao() {
        KakaoUnlinkClient clientWithoutKey = new KakaoUnlinkClient(
                RestClient.create(),
                "",
                URI.create(UNLINK_URL));

        assertThatThrownBy(() -> clientWithoutKey.unlink("12345"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
    }
}
