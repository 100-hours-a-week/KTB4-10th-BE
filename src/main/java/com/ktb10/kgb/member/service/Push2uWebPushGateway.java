package com.ktb10.kgb.member.service;

import com.the13haven.push2u.EndpointPolicies;
import com.the13haven.push2u.EndpointRule;
import com.the13haven.push2u.PushMessage;
import com.the13haven.push2u.PushOutcome;
import com.the13haven.push2u.PushSender;
import com.the13haven.push2u.Subscription;
import com.the13haven.push2u.Urgency;
import com.the13haven.push2u.VapidKeys;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Java 21 기반 push2u로 VAPID 인증과 payload 암호화를 수행합니다. */
@Component
public class Push2uWebPushGateway implements WebPushGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(Push2uWebPushGateway.class);
    private static final Duration MESSAGE_TTL = Duration.ofHours(1);

    private final PushSender pushSender;

    public Push2uWebPushGateway(
            @Value("${webpush.vapid.public-key:}") String publicKey,
            @Value("${webpush.vapid.private-key:}") String privateKey,
            @Value("${webpush.vapid.subject:}") String subject) {
        this.pushSender = createSender(publicKey, privateKey, subject);
    }

    @Override
    public boolean isConfigured() {
        return pushSender != null;
    }

    @Override
    public WebPushSendResult send(
            WebPushDeliveryTarget target,
            byte[] payload,
            String topic) {
        if (!isConfigured()) {
            return WebPushSendResult.rejected(null);
        }

        try {
            Subscription subscription = Subscription.fromBase64(
                    target.endpoint(), target.p256dh(), target.authSecret());
            PushMessage message = PushMessage.builder(payload)
                    .ttl(MESSAGE_TTL)
                    .urgency(Urgency.NORMAL)
                    .topic(topic)
                    .build();
            return classify(pushSender.send(subscription, message));
        } catch (IllegalArgumentException exception) {
            return WebPushSendResult.rejected(null);
        }
    }

    private PushSender createSender(String publicKey, String privateKey, String subject) {
        if (publicKey.isBlank() || privateKey.isBlank() || subject.isBlank()) {
            LOGGER.info("Web Push VAPID 설정이 완성되지 않아 외부 Push 전송을 비활성화합니다.");
            return null;
        }
        return PushSender.builder(
                        VapidKeys.fromBase64(publicKey, privateKey),
                        subject,
                        EndpointPolicies.allowedEndpoints(List.of(
                                EndpointRule.origin("https://fcm.googleapis.com"),
                                EndpointRule.domain("push.services.mozilla.com"),
                                EndpointRule.domain("push.apple.com"),
                                EndpointRule.domain("notify.windows.com"))))
                .build();
    }

    static WebPushSendResult classify(PushOutcome outcome) {
        if (outcome instanceof PushOutcome.Accepted accepted) {
            return WebPushSendResult.accepted(accepted.statusCode());
        }
        if (outcome instanceof PushOutcome.SubscriptionExpired expired) {
            return WebPushSendResult.expired(expired.statusCode());
        }
        if (outcome instanceof PushOutcome.RetryableFailure retryable) {
            return WebPushSendResult.retryable(
                    retryable.retryAfter().orElse(null), retryable.statusCode());
        }
        if (outcome instanceof PushOutcome.Indeterminate) {
            return WebPushSendResult.retryable(null, null);
        }
        if (outcome instanceof PushOutcome.SignerUnavailable unavailable) {
            Integer statusCode = unavailable.status().isPresent()
                    ? unavailable.status().getAsInt()
                    : null;
            return WebPushSendResult.retryable(
                    unavailable.retryAfter().orElse(null), statusCode);
        }
        if (outcome instanceof PushOutcome.NonRetryableFailure failure) {
            return WebPushSendResult.rejected(failure.statusCode());
        }
        return WebPushSendResult.rejected(null);
    }
}
