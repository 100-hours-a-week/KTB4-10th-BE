package com.ktb10.kgb.member.service;

import com.the13haven.push2u.EndpointAssessment;
import com.the13haven.push2u.EndpointPolicies;
import com.the13haven.push2u.EndpointPolicy;
import com.the13haven.push2u.EndpointRule;
import java.net.URI;
import org.springframework.stereotype.Component;

/** 등록과 발송에서 공통으로 사용하는 브라우저 Push Service 허용 정책입니다. */
@Component
public class WebPushEndpointPolicy {

    private final EndpointPolicy policy = EndpointPolicies.allowedEndpoints(
            EndpointRule.origin("https://fcm.googleapis.com"),
            EndpointRule.domain("push.services.mozilla.com"),
            EndpointRule.domain("push.apple.com"),
            EndpointRule.domain("notify.windows.com"));

    public boolean allows(URI endpoint) {
        return policy.assess(endpoint) instanceof EndpointAssessment.Allowed;
    }

    EndpointPolicy value() {
        return policy;
    }
}
