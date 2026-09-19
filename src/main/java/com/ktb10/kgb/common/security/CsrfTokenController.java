package com.ktb10.kgb.common.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.common.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 브라우저가 상태 변경 요청 전에 사용할 CSRF 토큰 계약을 제공합니다. */
@RestController
@RequestMapping("/api/v1/auth")
public class CsrfTokenController {

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<CsrfTokenResponse>> csrf(CsrfToken csrfToken) {
        CsrfTokenResponse data = new CsrfTokenResponse(
                "XSRF-TOKEN",
                csrfToken.getHeaderName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("CSRF 토큰 조회에 성공했습니다.", data));
    }

    public record CsrfTokenResponse(
            @JsonProperty("cookie_name") String cookieName,
            @JsonProperty("header_name") String headerName) {
    }
}
