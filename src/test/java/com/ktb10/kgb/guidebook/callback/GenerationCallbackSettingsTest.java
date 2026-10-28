package com.ktb10.kgb.guidebook.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GenerationCallbackSettingsTest {

    @Test
    void requiresTokenWithoutAnOptionalDeliveryMode() {
        assertThatThrownBy(() -> new GenerationCallbackSettings("", Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI_CALLBACK_TOKEN");
        assertThatThrownBy(() -> new GenerationCallbackSettings("   ", Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThatThrownBy(() -> new GenerationCallbackSettings("test-token", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GenerationCallbackSettings("test-token", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesConfiguredTokenAndFifteenMinuteDeadline() {
        var settings = new GenerationCallbackSettings("test-token", Duration.ofMinutes(15));

        assertThat(settings.token()).isEqualTo("test-token");
        assertThat(settings.timeout()).isEqualTo(Duration.ofMinutes(15));
    }
}
