package com.ktb10.kgb.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.the13haven.push2u.PushDeliveryException;
import com.the13haven.push2u.PushOutcome;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Push2uWebPushGatewayTest {

    @Test
    void classifiesAcceptedResponse() {
        WebPushSendResult result = Push2uWebPushGateway.classify(
                new PushOutcome.Accepted(201));

        assertThat(result.status()).isEqualTo(WebPushSendStatus.ACCEPTED);
        assertThat(result.statusCode()).isEqualTo(201);
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 410})
    void classifiesExpiredSubscription(int statusCode) {
        WebPushSendResult result = Push2uWebPushGateway.classify(
                new PushOutcome.SubscriptionExpired(statusCode));

        assertThat(result.status()).isEqualTo(WebPushSendStatus.EXPIRED);
        assertThat(result.statusCode()).isEqualTo(statusCode);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 502, 503, 504})
    void classifiesRetryableResponse(int statusCode) {
        WebPushSendResult result = Push2uWebPushGateway.classify(
                new PushOutcome.RetryableFailure(
                        statusCode,
                        Optional.of(Duration.ofSeconds(3))));

        assertThat(result.status()).isEqualTo(WebPushSendStatus.RETRYABLE);
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void classifiesConnectionFailureAsIndeterminateRetry() {
        PushDeliveryException failure = new PushDeliveryException(
                "Push 서비스 연결 실패",
                new IllegalStateException("연결 실패"));

        WebPushSendResult result = Push2uWebPushGateway.classify(
                new PushOutcome.Indeterminate(failure));

        assertThat(result.status()).isEqualTo(WebPushSendStatus.RETRYABLE);
        assertThat(result.statusCode()).isNull();
    }

    @Test
    void classifiesPermanentFailureWithoutRetry() {
        WebPushSendResult result = Push2uWebPushGateway.classify(
                new PushOutcome.NonRetryableFailure(403));

        assertThat(result.status()).isEqualTo(WebPushSendStatus.REJECTED);
        assertThat(result.statusCode()).isEqualTo(403);
    }
}
