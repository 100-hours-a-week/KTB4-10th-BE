package com.ktb10.kgb.guidebook.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Prod 환경에서 실제 AI 서버용 HTTP Client를 구성합니다. */
@Configuration
@Profile("prod")
@EnableConfigurationProperties(AiServerProperties.class)
public class GuidebookAiClientConfig {

    @Bean
    public GuidebookAiClient guidebookAiClient(
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
