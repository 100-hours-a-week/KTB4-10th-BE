package com.ktb10.kgb.content.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpTourApiClientTest {

    @Test
    void requestsAreaAndFestivalSnapshotsWithConfiguredParameters() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://apis.data.go.kr/B551011/KorService2");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        byte[] response = "{\"response\":{}}".getBytes(StandardCharsets.UTF_8);
        server.expect(request -> {
            assertThat(request.getURI().getPath()).endsWith("/areaBasedList2");
            assertThat(request.getURI().getRawQuery())
                    .contains("serviceKey=service%2Bkey%2F%3D");
            assertThat(request.getURI().getQuery())
                    .contains("pageNo=1")
                    .contains("numOfRows=60000");
        }).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        server.expect(request -> {
            assertThat(request.getURI().getPath()).endsWith("/searchFestival2");
            assertThat(request.getURI().getQuery())
                    .contains("eventStartDate=20260711")
                    .contains("eventEndDate=20281009");
        }).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        TourApiClient client = new HttpTourApiClient(
                builder.build(),
                TourApiTestFixtures.properties("data/tourism/raw", "service+key/="));

        assertThat(client.fetchAreaSnapshot()).isEqualTo(response);
        assertThat(client.fetchFestivalSnapshot(
                LocalDate.of(2026, 7, 11),
                LocalDate.of(2028, 10, 9))).isEqualTo(response);
        server.verify();
    }

    @Test
    void marksServerErrorAsRetryable() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://apis.data.go.kr/B551011/KorService2");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> { }).andRespond(withServerError());
        TourApiClient client = new HttpTourApiClient(
                builder.build(),
                TourApiTestFixtures.properties("data/tourism/raw"));

        assertThatThrownBy(client::fetchAreaSnapshot)
                .isInstanceOf(TourApiException.class)
                .extracting(exception -> ((TourApiException) exception).isRetryable())
                .isEqualTo(true);
    }
}
