package com.ktb10.kgb.tools.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb10.kgb.member.service.WebPushDeliveryTarget;
import com.ktb10.kgb.member.service.WebPushSendResult;
import com.ktb10.kgb.member.service.WebPushSendStatus;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoadTestWebPushGatewayTest {

    private static final WebPushDeliveryTarget TARGET =
            new WebPushDeliveryTarget(1L, "endpoint", "p256dh", "auth");

    @Test
    @DisplayName("설정한 비율 순서대로 지연 성공과 재시도 및 timeout을 재현한다")
    void reproducesConfiguredOutcomesDeterministically() {
        LoadTestWebPushGateway gateway = new LoadTestWebPushGateway(
                true,
                1,
                1,
                1,
                1,
                Duration.ZERO,
                Duration.ZERO);

        assertThat(send(gateway).status()).isEqualTo(WebPushSendStatus.ACCEPTED);
        assertThat(send(gateway).status()).isEqualTo(WebPushSendStatus.RETRYABLE);
        assertThat(send(gateway).statusCode()).isEqualTo(503);
        assertThatThrownBy(() -> send(gateway))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("timeout");
        assertThat(send(gateway).status()).isEqualTo(WebPushSendStatus.ACCEPTED);
    }

    @Test
    @DisplayName("응답 비율 합이 100을 넘으면 설정 오류로 중단한다")
    void rejectsRatioSumOverOneHundred() {
        assertThatThrownBy(() -> new LoadTestWebPushGateway(
                true,
                30,
                30,
                30,
                30,
                Duration.ZERO,
                Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100 이하");
    }

    private WebPushSendResult send(LoadTestWebPushGateway gateway) {
        return gateway.send(TARGET, new byte[0], "topic");
    }
}
