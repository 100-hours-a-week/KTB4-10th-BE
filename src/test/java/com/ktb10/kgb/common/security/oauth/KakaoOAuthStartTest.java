package com.ktb10.kgb.common.security.oauth;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kakao-oauth-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "KAKAO_REST_API_KEY=test-rest-api-key",
        "KAKAO_CLIENT_SECRET=test-client-secret",
        "KAKAO_REDIRECT_URI=http://localhost:8080/api/v1/auth/oauth/callback/kakao"
})
@AutoConfigureMockMvc
class KakaoOAuthStartTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void redirectsToKakaoWithStateAndPkce() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/oauth/authorize/kakao"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-cache")))
                .andExpect(header().string(HttpHeaders.LOCATION, not(blankOrNullString())))
                .andReturn();

        URI location = URI.create(result.getResponse().getHeader(HttpHeaders.LOCATION));
        var query = UriComponentsBuilder.fromUri(location).build().getQueryParams();

        org.assertj.core.api.Assertions.assertThat(location.getScheme()).isEqualTo("https");
        org.assertj.core.api.Assertions.assertThat(location.getHost()).isEqualTo("kauth.kakao.com");
        org.assertj.core.api.Assertions.assertThat(location.getPath()).isEqualTo("/oauth/authorize");
        org.assertj.core.api.Assertions.assertThat(query.getFirst("response_type")).isEqualTo("code");
        org.assertj.core.api.Assertions.assertThat(query.getFirst("client_id")).isEqualTo("test-rest-api-key");
        org.assertj.core.api.Assertions.assertThat(query.getFirst("redirect_uri"))
                .isEqualTo("http://localhost:8080/api/v1/auth/oauth/callback/kakao");
        org.assertj.core.api.Assertions.assertThat(query.getFirst("state")).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(query.getFirst("code_challenge")).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
        org.assertj.core.api.Assertions.assertThat(query).doesNotContainKey("scope");
        org.assertj.core.api.Assertions.assertThat(result.getRequest().getSession(false)).isNotNull();
    }

    @Test
    void unsupportedProviderDoesNotStartOAuth() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/authorize/google"))
                .andExpect(status().isBadRequest());
    }
}
