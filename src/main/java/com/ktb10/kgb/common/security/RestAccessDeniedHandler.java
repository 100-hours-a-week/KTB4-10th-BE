package com.ktb10.kgb.common.security;

import com.ktb10.kgb.common.error.CommonErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 인증된 회원의 권한 부족을 공통 403 응답으로 반환합니다. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final RestSecurityErrorWriter errorWriter;

    public RestAccessDeniedHandler(RestSecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        errorWriter.write(request, response, CommonErrorCode.RESOURCE_FORBIDDEN);
    }
}
