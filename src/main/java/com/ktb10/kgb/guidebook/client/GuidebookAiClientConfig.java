package com.ktb10.kgb.guidebook.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 운영 및 로컬 HTTP 모드에서 AI 서버용 Client를 구성합니다. */
@Configuration
@Profile({"local", "prod"})
@EnableConfigurationProperties(AiServerProperties.class)
public class GuidebookAiClientConfig {

    @Bean
    @Profile("prod")
    public GuidebookAiClient guidebookAiClient(
            RestClient.Builder builder,
            AiServerProperties properties) {
        return createHttpClient(builder, properties);
    }

    @Bean
    @Profile("local & !prod")
    @ConditionalOnProperty(name = "ai.client.mode", havingValue = "http")
    public GuidebookAiClient localHttpGuidebookAiClient(
            RestClient.Builder builder,
            AiServerProperties properties) {
        return createHttpClient(builder, properties);
    }

    private GuidebookAiClient createHttpClient(
            RestClient.Builder builder,
            AiServerProperties properties) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient restClient = builder
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        return new HttpGuidebookAiClient(restClient);
    }
}
