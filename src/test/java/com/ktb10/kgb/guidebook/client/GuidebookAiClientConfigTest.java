package com.ktb10.kgb.guidebook.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class GuidebookAiClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(GuidebookAiClientConfig.class)
            .withBean(RestClient.Builder.class, RestClient::builder);

    @Test
    void localAlwaysUsesHttp() {
        contextRunner.withPropertyValues("spring.profiles.active=local")
                .run(context -> {
                    assertThat(context).hasSingleBean(GuidebookAiClient.class);
                    assertThat(context.getBean(GuidebookAiClient.class))
                            .isInstanceOf(HttpGuidebookAiClient.class);
                });
    }

    @Test
    void localHttpModeUsesConfiguredServer() {
        contextRunner.withPropertyValues(
                        "spring.profiles.active=local",
                        "AI_SERVER_BASE_URL=http://localhost:18000",
                        "AI_SERVER_CONNECT_TIMEOUT=2s",
                        "AI_SERVER_READ_TIMEOUT=5s")
                .run(context -> {
                    assertThat(context).hasSingleBean(GuidebookAiClient.class);
                    assertThat(context.getBean(GuidebookAiClient.class))
                            .isInstanceOf(HttpGuidebookAiClient.class);
                    AiServerProperties properties = context.getBean(AiServerProperties.class);
                    assertThat(properties.baseUrl().toString()).isEqualTo("http://localhost:18000");
                    assertThat(properties.connectTimeout()).hasSeconds(2);
                    assertThat(properties.readTimeout()).hasSeconds(5);
                });
    }

    @Test
    void localHttpModeDefaultsToDockerPortAndTimeouts() {
        contextRunner.withPropertyValues("spring.profiles.active=local")
                .run(context -> {
                    assertThat(context).hasSingleBean(GuidebookAiClient.class);
                    assertThat(context.getBean(GuidebookAiClient.class))
                            .isInstanceOf(HttpGuidebookAiClient.class);
                    AiServerProperties properties = context.getBean(AiServerProperties.class);
                    assertThat(properties.baseUrl().toString()).isEqualTo("http://localhost:8000");
                    assertThat(properties.connectTimeout()).hasSeconds(3);
                    assertThat(properties.readTimeout()).hasSeconds(10);
                });
    }

    @Test
    void prodAlwaysUsesHttp() {
        contextRunner.withPropertyValues(
                        "spring.profiles.active=prod",
                        "AI_SERVER_BASE_URL=http://ai.internal:8000")
                .run(context -> {
                    assertThat(context).hasSingleBean(GuidebookAiClient.class);
                    assertThat(context.getBean(GuidebookAiClient.class))
                            .isInstanceOf(HttpGuidebookAiClient.class);
                });
    }

    @Test
    void combinedLocalAndProdProfilesRegisterOnlyProductionHttpClient() {
        contextRunner.withPropertyValues(
                        "spring.profiles.active=local,prod",
                        "AI_SERVER_BASE_URL=http://ai.internal:8000")
                .run(context -> {
                    assertThat(context).hasSingleBean(GuidebookAiClient.class);
                    assertThat(context.getBean(GuidebookAiClient.class))
                            .isInstanceOf(HttpGuidebookAiClient.class);
                });
    }

    @Test
    void otherProfilesDoNotRegisterAiClients() {
        contextRunner.withPropertyValues("spring.profiles.active=test")
                .run(context -> assertThat(context).doesNotHaveBean(GuidebookAiClient.class));
    }
}
