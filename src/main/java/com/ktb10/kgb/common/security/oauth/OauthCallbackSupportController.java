package com.ktb10.kgb.common.security.oauth;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OAuth 필터가 처리하지 않은 공급자와 FE 없는 개발 환경의 실패 결과를 처리합니다. */
@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OauthCallbackSupportController {

    @GetMapping("/authorize/{provider}")
    public void rejectUnsupportedProvider(@PathVariable String provider) {
        throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_ERROR);
    }

    @GetMapping("/error")
    public void showOauthError(@RequestParam OauthErrorCode code) {
        throw new BusinessException(code);
    }
}
