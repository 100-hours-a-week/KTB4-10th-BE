package com.ktb10.kgb.common.security;

import com.ktb10.kgb.common.error.CommonErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 인증이 필요한 요청의 401 응답을 공통 형식으로 반환합니다. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final RestSecurityErrorWriter errorWriter;

    public RestAuthenticationEntryPoint(RestSecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException, ServletException {
        errorWriter.write(request, response, CommonErrorCode.AUTH_SESSION_REQUIRED);
    }
}
