package com.ktb10.kgb.common.security;

import com.ktb10.kgb.member.entity.MemberStatus;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/** 인증된 회원이 초기 취향 입력을 마친 ACTIVE 상태인지 확인합니다. */
@Component
public class ActiveMemberAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    @SuppressWarnings("deprecation")
    public AuthorizationDecision check(
            Supplier<Authentication> authenticationSupplier,
            RequestAuthorizationContext context) {
        Authentication authentication = authenticationSupplier.get();
        boolean granted = authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedMember member
                && member.status() == MemberStatus.ACTIVE;
        return new AuthorizationDecision(granted);
    }
}
