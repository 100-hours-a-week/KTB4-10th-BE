package com.ktb10.kgb.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebPushEndpointPolicyTest {

    private final WebPushEndpointPolicy policy = new WebPushEndpointPolicy();

    @ParameterizedTest
    @ValueSource(strings = {
        "https://fcm.googleapis.com/fcm/send/chrome-token",
        "https://updates.push.services.mozilla.com/wpush/v2/firefox-token",
        "https://web.push.apple.com/QD/safari-token",
        "https://wns2-notify.notify.windows.com/w/?token=windows-token"
    })
    void allowsKnownBrowserPushServices(String endpoint) {
        assertThat(policy.allows(URI.create(endpoint))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://127.0.0.1/push",
        "https://[::1]/push",
        "https://10.0.0.10/push",
        "https://169.254.169.254/latest/meta-data",
        "https://unknown.example/push",
        "https://fcm.googleapis.com:8443/fcm/send/token",
        "https://evil.example/?next=https://fcm.googleapis.com"
    })
    void rejectsEndpointsOutsideKnownPushServices(String endpoint) {
        assertThat(policy.allows(URI.create(endpoint))).isFalse();
    }
}
