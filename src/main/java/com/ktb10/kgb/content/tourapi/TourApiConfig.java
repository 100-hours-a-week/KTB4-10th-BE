package com.ktb10.kgb.content.tourapi;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.s3.S3Client;

/** TourAPI 수집용 HTTP Client와 선택적인 S3 Client를 구성합니다. */
@Configuration
@EnableConfigurationProperties({
    TourApiProperties.class,
    TourApiSchedulerProperties.class,
    TourApiDetailBackfillProperties.class
})
public class TourApiConfig {

    @Bean
    public TourApiClient tourApiClient(
            RestClient.Builder builder,
            TourApiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = builder
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        return new HttpTourApiClient(restClient, properties);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnExpression(
            "'${tour-api.collection.snapshot-location:data/tourism/raw}'"
                    + ".startsWith('s3://')")
    public S3Client tourApiSnapshotS3Client() {
        return S3Client.create();
    }

    @Bean
    public TourApiSnapshotStorage tourApiSnapshotStorage(
            TourApiProperties properties,
            ObjectProvider<S3Client> s3ClientProvider) {
        return new TourApiSnapshotStorage(properties, s3ClientProvider::getIfAvailable);
    }
}
