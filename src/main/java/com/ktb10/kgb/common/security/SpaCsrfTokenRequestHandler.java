package com.ktb10.kgb.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/** SPA에는 원문 헤더를 허용하고 응답에는 BREACH 방어용 요청 속성을 제공합니다. */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plainHandler = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xorHandler = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            Supplier<CsrfToken> csrfToken) {
        xorHandler.handle(request, response, csrfToken);
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return StringUtils.hasText(headerValue)
                ? plainHandler.resolveCsrfTokenValue(request, csrfToken)
                : xorHandler.resolveCsrfTokenValue(request, csrfToken);
    }
}
