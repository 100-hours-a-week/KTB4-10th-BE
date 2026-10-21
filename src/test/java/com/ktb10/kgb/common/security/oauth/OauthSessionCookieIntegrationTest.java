package com.ktb10.kgb.common.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:oauth-session-cookie-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "management.server.port=0",
                "KAKAO_REST_API_KEY=test-rest-api-key",
                "KAKAO_CLIENT_SECRET=test-client-secret",
                "KAKAO_REDIRECT_URI=http://localhost:8080/api/v1/auth/oauth/callback/kakao",
                "SESSION_COOKIE_SECURE=true"
        })
class OauthSessionCookieIntegrationTest extends RedisIntegrationTestSupport {

    @LocalServerPort
    private int serverPort;

    @Test
    void oauthStartIssuesSecuredTemporarySessionCookie()
            throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort
                        + "/api/v1/auth/oauth/authorize/kakao"))
                .GET()
                .build();

        HttpResponse<Void> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.discarding());

        String sessionCookie = response.headers().allValues("set-cookie").stream()
                .filter(cookie -> cookie.startsWith("JSESSIONID="))
                .findFirst()
                .orElseThrow();

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(sessionCookie)
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax");
    }
}
