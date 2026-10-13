package com.ktb10.kgb.guidebook.pdf;

import java.util.concurrent.Semaphore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 격리된 Gotenberg Chromium 변환기 연결을 구성합니다. */
@Configuration
@EnableConfigurationProperties(GuidebookPdfProperties.class)
public class GuidebookPdfConfig {

    @Bean
    public RestClient guidebookPdfRestClient(
            RestClient.Builder builder,
            GuidebookPdfProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public Semaphore guidebookPdfSemaphore(GuidebookPdfProperties properties) {
        return new Semaphore(properties.maxConcurrentConversions(), true);
    }
}
