package com.ktb10.kgb.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.member.service.ServiceSessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(OutputCaptureExtension.class)
class SessionAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void internalAuthenticationFailureUsesStructuredErrorFields(CapturedOutput output)
            throws Exception {
        ServiceSessionService serviceSessionService = mock(ServiceSessionService.class);
        when(serviceSessionService.authenticate("broken-session"))
                .thenThrow(new IllegalStateException("database unavailable"));
        SessionAuthenticationFilter filter = new SessionAuthenticationFilter(
                new SessionCookieResolver(),
                serviceSessionService,
                new RestSecurityErrorWriter(new ObjectMapper()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members/me");
        request.setCookies(new Cookie(SessionCookieResolver.COOKIE_NAME, "broken-session"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(output)
                .contains("event=service_session_authentication_failure")
                .contains("method=GET")
                .contains("route=/api/v1/members/me")
                .contains("status=500")
                .contains("errorCode=INTERNAL_SERVER_ERROR")
                .contains("rootCauseType=IllegalStateException");
    }
}
