package com.ktb10.kgb.common.security.oauth;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 서비스 탈퇴 시 카카오 앱과 사용자의 연결을 해제합니다. */
@Component
public class KakaoUnlinkClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final String ADMIN_AUTHORIZATION_PREFIX = "KakaoAK ";
    private static final String TARGET_ID_TYPE = "user_id";

    private final RestClient restClient;
    private final String adminKey;
    private final URI unlinkUri;

    @Autowired
    public KakaoUnlinkClient(
            RestClient.Builder builder,
            @Value("${KAKAO_ADMIN_KEY:}") String adminKey,
            @Value("${KAKAO_UNLINK_URI:https://kapi.kakao.com/v1/user/unlink}") URI unlinkUri) {
        this(createRestClient(builder), adminKey, unlinkUri);
    }

    KakaoUnlinkClient(RestClient restClient, String adminKey, URI unlinkUri) {
        this.restClient = Objects.requireNonNull(restClient, "REST 클라이언트는 null일 수 없습니다.");
        this.adminKey = Objects.requireNonNull(adminKey, "카카오 어드민 키는 null일 수 없습니다.");
        this.unlinkUri = Objects.requireNonNull(unlinkUri, "카카오 연결 해제 URI는 null일 수 없습니다.");
    }

    public void unlink(String oauthSubject) {
        if (adminKey.isBlank()) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
        long kakaoUserId = parseKakaoUserId(oauthSubject);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", TARGET_ID_TYPE);
        form.add("target_id", Long.toString(kakaoUserId));

        try {
            UnlinkResponse response = restClient.post()
                    .uri(unlinkUri)
                    .header("Authorization", ADMIN_AUTHORIZATION_PREFIX + adminKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(UnlinkResponse.class);
            if (response == null || response.id() == null || response.id() != kakaoUserId) {
                throw new BusinessException(CommonErrorCode.UPSTREAM_SERVICE_ERROR);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException(CommonErrorCode.UPSTREAM_SERVICE_ERROR, exception);
        }
    }

    private static RestClient createRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return builder.requestFactory(requestFactory).build();
    }

    private static long parseKakaoUserId(String oauthSubject) {
        try {
            return Long.parseLong(oauthSubject);
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }

    private record UnlinkResponse(Long id) {
    }
}
