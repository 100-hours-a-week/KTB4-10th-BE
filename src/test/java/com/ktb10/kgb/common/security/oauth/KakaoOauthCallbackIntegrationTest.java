package com.ktb10.kgb.common.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kakao-callback-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "KAKAO_REST_API_KEY=test-rest-api-key",
        "KAKAO_CLIENT_SECRET=test-client-secret",
        "KAKAO_REDIRECT_URI=http://localhost:8080/api/v1/auth/oauth/callback/kakao",
        "SESSION_COOKIE_SECURE=false"
})
@AutoConfigureMockMvc
class KakaoOauthCallbackIntegrationTest {

    private static final AtomicReference<String> TOKEN_REQUEST = new AtomicReference<>();
    private static final AtomicReference<String> USER_AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<FailureMode> FAILURE_MODE =
            new AtomicReference<>(FailureMode.NONE);
    private static final HttpServer KAKAO_SERVER = startKakaoServer();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private CreditWalletRepository creditWalletRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @Autowired
    private SessionIdHasher sessionIdHasher;

    @DynamicPropertySource
    static void kakaoEndpointProperties(DynamicPropertyRegistry registry) {
        String baseUri = "http://localhost:" + KAKAO_SERVER.getAddress().getPort();
        registry.add("KAKAO_AUTHORIZATION_URI", () -> baseUri + "/oauth/authorize");
        registry.add("KAKAO_TOKEN_URI", () -> baseUri + "/oauth/token");
        registry.add("KAKAO_USER_INFO_URI", () -> baseUri + "/v2/user/me");
    }

    @BeforeEach
    void cleanUp() {
        authSessionRepository.deleteAll();
        creditTransactionRepository.deleteAll();
        creditWalletRepository.deleteAll();
        memberRepository.deleteAll();
        TOKEN_REQUEST.set(null);
        USER_AUTHORIZATION.set(null);
        FAILURE_MODE.set(FailureMode.NONE);
    }

    @AfterAll
    static void stopKakaoServer() {
        KAKAO_SERVER.stop(0);
    }

    @Test
    void callbackCreatesMemberCreditsAndServiceSession() throws Exception {
        CallbackAttempt attempt = startLogin();

        MvcResult callback = mockMvc.perform(get("/api/v1/auth/oauth/callback/kakao")
                        .session(attempt.session())
                        .queryParam("code", "valid-authorization-code")
                        .queryParam("state", attempt.state()))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/members/me"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();

        String setCookie = callback.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith("KGB_SESSION="))
                .findFirst()
                .orElseThrow();
        String rawSessionId = setCookie.substring(
                "KGB_SESSION=".length(),
                setCookie.indexOf(';'));
        var member = memberRepository
                .findByOauthProviderAndOauthSubjectAndDeletedAtIsNull(
                        OauthProvider.KAKAO,
                        "987654321")
                .orElseThrow();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ONBOARDING);
        assertThat(member.getNickname()).isEqualTo("카카오 여행자");
        assertThat(member.getEmail()).isNull();
        assertThat(creditWalletRepository.findByMemberId(member.getId()).orElseThrow()
                .getCreditBalance()).isEqualTo(3);
        assertThat(authSessionRepository.findBySessionIdHash(sessionIdHasher.hash(rawSessionId)))
                .isPresent();
        assertThat(TOKEN_REQUEST.get())
                .contains("client_secret=test-client-secret")
                .contains("code=valid-authorization-code")
                .contains("code_verifier=");
        assertThat(USER_AUTHORIZATION.get()).isEqualTo("Bearer kakao-access-token");
    }

    @Test
    void tokenEndpointUnavailableDoesNotCreateLoginData() throws Exception {
        CallbackAttempt attempt = startLogin();
        FAILURE_MODE.set(FailureMode.TOKEN_UNAVAILABLE);

        mockMvc.perform(get("/api/v1/auth/oauth/callback/kakao")
                        .session(attempt.session())
                        .queryParam("code", "valid-authorization-code")
                        .queryParam("state", attempt.state()))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        "/api/v1/auth/oauth/error?code=OAUTH_PROVIDER_UNAVAILABLE"));

        assertNoLoginDataCreated();
    }

    @Test
    void userInfoEndpointUnavailableDoesNotCreateLoginData() throws Exception {
        CallbackAttempt attempt = startLogin();
        FAILURE_MODE.set(FailureMode.USER_INFO_UNAVAILABLE);

        mockMvc.perform(get("/api/v1/auth/oauth/callback/kakao")
                        .session(attempt.session())
                        .queryParam("code", "valid-authorization-code")
                        .queryParam("state", attempt.state()))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        "/api/v1/auth/oauth/error?code=OAUTH_PROVIDER_UNAVAILABLE"));

        assertNoLoginDataCreated();
    }

    private CallbackAttempt startLogin() throws Exception {
        MvcResult start = mockMvc.perform(get("/api/v1/auth/oauth/authorize/kakao"))
                .andExpect(status().isFound())
                .andReturn();
        URI authorizationUri = URI.create(start.getResponse().getHeader(HttpHeaders.LOCATION));
        String encodedState = UriComponentsBuilder.fromUri(authorizationUri)
                .build()
                .getQueryParams()
                .getFirst("state");
        String state = URLDecoder.decode(encodedState, StandardCharsets.UTF_8);
        MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);
        return new CallbackAttempt(session, state);
    }

    private void assertNoLoginDataCreated() {
        assertThat(memberRepository.count()).isZero();
        assertThat(authSessionRepository.count()).isZero();
        assertThat(creditWalletRepository.count()).isZero();
        assertThat(creditTransactionRepository.count()).isZero();
    }

    private static HttpServer startKakaoServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/oauth/token", KakaoOauthCallbackIntegrationTest::tokenResponse);
            server.createContext("/v2/user/me", KakaoOauthCallbackIntegrationTest::userResponse);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("테스트용 카카오 서버를 시작할 수 없습니다.", exception);
        }
    }

    private static void tokenResponse(HttpExchange exchange) throws IOException {
        TOKEN_REQUEST.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (FAILURE_MODE.get() == FailureMode.TOKEN_UNAVAILABLE) {
            respondJson(exchange, 503, "{\"error\":\"temporarily_unavailable\"}");
            return;
        }
        respondJson(exchange, """
                {
                  "token_type": "bearer",
                  "access_token": "kakao-access-token",
                  "expires_in": 3600,
                  "refresh_token": "kakao-refresh-token",
                  "refresh_token_expires_in": 5184000
                }
                """);
    }

    private static void userResponse(HttpExchange exchange) throws IOException {
        USER_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (FAILURE_MODE.get() == FailureMode.USER_INFO_UNAVAILABLE) {
            respondJson(exchange, 503, "{\"error\":\"temporarily_unavailable\"}");
            return;
        }
        respondJson(exchange, """
                {
                  "id": 987654321,
                  "kakao_account": {
                    "profile": {
                      "nickname": "카카오 여행자",
                      "profile_image_url": "https://example.com/profile.png"
                    }
                  }
                }
                """);
    }

    private static void respondJson(HttpExchange exchange, String body) throws IOException {
        respondJson(exchange, 200, body);
    }

    private static void respondJson(HttpExchange exchange, int statusCode, String body)
            throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        exchange.sendResponseHeaders(statusCode, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private record CallbackAttempt(MockHttpSession session, String state) {
    }

    private enum FailureMode {
        NONE,
        TOKEN_UNAVAILABLE,
        USER_INFO_UNAVAILABLE
    }
}
