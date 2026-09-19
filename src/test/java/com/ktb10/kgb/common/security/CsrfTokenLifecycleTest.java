package com.ktb10.kgb.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

class CsrfTokenLifecycleTest {

    @Test
    void clearExpiresCsrfCookieAtAuthenticationBoundary() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        CsrfTokenLifecycle lifecycle = new CsrfTokenLifecycle(repository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("XSRF-TOKEN", "previous-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        lifecycle.clear(request, response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("XSRF-TOKEN=")
                .contains("Max-Age=0")
                .contains("Path=/");
    }
}
