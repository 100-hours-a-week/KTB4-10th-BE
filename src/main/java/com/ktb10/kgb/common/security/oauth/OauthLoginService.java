package com.ktb10.kgb.common.security.oauth;

import com.ktb10.kgb.common.security.SecureSessionIdGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** OAuth 사용자의 동시 가입 충돌을 복구하며 로그인 트랜잭션을 조정합니다. */
@Service
public class OauthLoginService {

    private final OauthLoginTransactionService transactionService;
    private final SecureSessionIdGenerator sessionIdGenerator;

    public OauthLoginService(
            OauthLoginTransactionService transactionService,
            SecureSessionIdGenerator sessionIdGenerator) {
        this.transactionService = transactionService;
        this.sessionIdGenerator = sessionIdGenerator;
    }

    public OauthLoginResult login(KakaoOauthUser oauthUser) {
        try {
            return transactionService.complete(oauthUser, sessionIdGenerator.generate());
        } catch (DataIntegrityViolationException firstFailure) {
            try {
                return transactionService.complete(oauthUser, sessionIdGenerator.generate());
            } catch (DataIntegrityViolationException retryFailure) {
                retryFailure.addSuppressed(firstFailure);
                throw retryFailure;
            }
        }
    }
}
