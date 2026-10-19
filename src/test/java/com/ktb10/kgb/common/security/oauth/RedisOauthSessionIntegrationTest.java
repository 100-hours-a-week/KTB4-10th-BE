package com.ktb10.kgb.common.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
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
                "management.server.port=0",
                "KAKAO_REST_API_KEY=test-rest-api-key",
                "KAKAO_CLIENT_SECRET=test-client-secret",
                "KAKAO_REDIRECT_URI=http://localhost/api/v1/auth/oauth/callback/kakao",
                "SESSION_COOKIE_SECURE=false"
        })
class RedisOauthSessionIntegrationTest {

    private static final String SESSION_PATTERN = "kgb:test:oauth:session:sessions:*";
    private static final String AUTHORIZATION_REQUESTS_FIELD = "sessionAttr:"
            + ExpiringAuthorizationRequestRepository.class.getName()
            + ".AUTHORIZATION_REQUESTS";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @LocalServerPort
    private int serverPort;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Test
    void oauthAuthorizationRequestIsStoredAsExpiringJsonRedisSession()
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

        Set<String> sessionKeys = redisTemplate.keys(SESSION_PATTERN);
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().allValues("set-cookie"))
                .anyMatch(cookie -> cookie.startsWith("JSESSIONID="));
        assertThat(sessionKeys).hasSize(1);

        String sessionKey = sessionKeys.iterator().next();
        assertThat(redisTemplate.getExpire(sessionKey)).isBetween(840L, 900L);

        byte[] serializedRequests = connectionFactory.getConnection()
                .hashCommands()
                .hGet(
                        sessionKey.getBytes(StandardCharsets.UTF_8),
                        AUTHORIZATION_REQUESTS_FIELD.getBytes(StandardCharsets.UTF_8));
        assertThat(serializedRequests).isNotEmpty();
        assertThat((char) serializedRequests[0]).isEqualTo('{');
    }
}
