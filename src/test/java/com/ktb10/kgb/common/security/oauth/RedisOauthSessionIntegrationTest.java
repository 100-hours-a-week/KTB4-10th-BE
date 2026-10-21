package com.ktb10.kgb.common.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:redis-oauth-session-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=",
                "spring.session.timeout=15m",
                "spring.session.redis.namespace=kgb:test:oauth:session",
                "kgb.oauth.redis.namespace=kgb:test:oauth:authorization-request",
                "management.server.port=0",
                "KAKAO_REST_API_KEY=test-rest-api-key",
                "KAKAO_CLIENT_SECRET=test-client-secret",
                "KAKAO_REDIRECT_URI=http://localhost/api/v1/auth/oauth/callback/kakao",
                "SESSION_COOKIE_SECURE=false"
        })
class RedisOauthSessionIntegrationTest extends RedisIntegrationTestSupport {

    private static final String SESSION_PATTERN = "kgb:test:oauth:session:sessions:*";
    private static final String ATTEMPT_PATTERN =
            "kgb:test:oauth:authorization-request:attempt:*";
    private static final String INDEX_PATTERN =
            "kgb:test:oauth:authorization-request:index:*";

    @LocalServerPort
    private int serverPort;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Autowired
    private AuthorizationRequestRepository<OAuth2AuthorizationRequest> repository;

    @Autowired
    private RedisAuthorizationRequestRepository redisRepository;

    @Autowired
    @Qualifier("oauthAuthorizationRequestRedisTemplate")
    private RedisTemplate<String, Object> oauthRedisTemplate;

    @BeforeEach
    void clearRedis() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void oauthStartStoresRequestSeparatelyFromExpiringHttpSession()
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort
                        + "/api/v1/auth/oauth/authorize/kakao"))
                .GET()
                .build();

        HttpResponse<Void> response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(request, HttpResponse.BodyHandlers.discarding());

        Set<String> sessionKeys = stringRedisTemplate.keys(SESSION_PATTERN);
        Set<String> attemptKeys = stringRedisTemplate.keys(ATTEMPT_PATTERN);
        Set<String> indexKeys = stringRedisTemplate.keys(INDEX_PATTERN);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().allValues("set-cookie"))
                .anyMatch(cookie -> cookie.startsWith("JSESSIONID="));
        assertThat(sessionKeys).hasSize(1);
        assertThat(attemptKeys).hasSize(1);
        assertThat(indexKeys).hasSize(1);
        assertThat(stringRedisTemplate.getExpire(attemptKeys.iterator().next()))
                .isBetween(540L, 600L);
    }

    @Test
    void keepsOnlyFiveMostRecentAttemptsPerBrowserSession() throws InterruptedException {
        MockHttpSession session = new MockHttpSession(null, "browser-session-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int index = 0; index < 6; index++) {
            repository.saveAuthorizationRequest(
                    authorizationRequest("state-" + index),
                    request(session, null),
                    response);
            Thread.sleep(Duration.ofMillis(2));
        }

        assertThat(repository.loadAuthorizationRequest(request(session, "state-0"))).isNull();
        assertThat(repository.loadAuthorizationRequest(request(session, "state-1"))).isNotNull();
        assertThat(repository.loadAuthorizationRequest(request(session, "state-5"))).isNotNull();
        assertThat(stringRedisTemplate.keys(ATTEMPT_PATTERN)).hasSize(5);
        String indexKey = stringRedisTemplate.keys(INDEX_PATTERN).iterator().next();
        assertThat(oauthRedisTemplate.opsForZSet().zCard(indexKey)).isEqualTo(5L);
    }

    @Test
    void concurrentCallbacksConsumeStateOnlyOnce() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(
                authorizationRequest("single-use-state"),
                request(session, null),
                response);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<OAuth2AuthorizationRequest>> results = List.of(
                    executor.submit(() -> consume(session, ready, start)),
                    executor.submit(() -> consume(session, ready, start)));
            ready.await();
            start.countDown();

            assertThat(results)
                    .extracting(Future::get)
                    .filteredOn(java.util.Objects::nonNull)
                    .hasSize(1);
        }

        assertThat(stringRedisTemplate.keys(ATTEMPT_PATTERN)).isEmpty();
        assertThat(stringRedisTemplate.keys(INDEX_PATTERN)).isEmpty();
    }

    @Test
    void restoresPkceVerifierFromRedis() {
        MockHttpSession session = new MockHttpSession(null, "pkce-browser-session-secret");
        OAuth2AuthorizationRequest authorizationRequest =
                OAuth2AuthorizationRequest.authorizationCode()
                        .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                        .clientId("test-client-id")
                        .redirectUri("http://localhost/api/v1/auth/oauth/callback/kakao")
                        .state("pkce-state")
                        .attributes(attributes ->
                                attributes.put("code_verifier", "pkce-verifier"))
                        .build();

        repository.saveAuthorizationRequest(
                authorizationRequest,
                request(session, null),
                new MockHttpServletResponse());

        OAuth2AuthorizationRequest restored = repository.loadAuthorizationRequest(
                request(session, "pkce-state"));
        assertThat(restored).isNotNull();
        assertThat((String) restored.getAttribute("code_verifier"))
                .isEqualTo("pkce-verifier");
        assertThat(stringRedisTemplate.keys("kgb:test:oauth:authorization-request:*"))
                .allMatch(key -> !key.contains("pkce-state"))
                .allMatch(key -> !key.contains(session.getId()));
    }

    @Test
    void deletesAllRemainingAttemptsWhenLoginSucceeds() {
        MockHttpSession session = new MockHttpSession();
        repository.saveAuthorizationRequest(
                authorizationRequest("first-state"),
                request(session, null),
                new MockHttpServletResponse());
        repository.saveAuthorizationRequest(
                authorizationRequest("second-state"),
                request(session, null),
                new MockHttpServletResponse());

        redisRepository.deleteAllForSession(request(session, null));

        assertThat(stringRedisTemplate.keys(ATTEMPT_PATTERN)).isEmpty();
        assertThat(stringRedisTemplate.keys(INDEX_PATTERN)).isEmpty();
    }

    private OAuth2AuthorizationRequest consume(
            MockHttpSession session,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return repository.removeAuthorizationRequest(
                request(session, "single-use-state"),
                new MockHttpServletResponse());
    }

    private static OAuth2AuthorizationRequest authorizationRequest(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .clientId("test-client-id")
                .redirectUri("http://localhost/api/v1/auth/oauth/callback/kakao")
                .state(state)
                .attributes(attributes -> attributes.put("code_verifier", "verifier-" + state))
                .build();
    }

    private static MockHttpServletRequest request(MockHttpSession session, String state) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        if (state != null) {
            request.setParameter("state", state);
        }
        return request;
    }
}
