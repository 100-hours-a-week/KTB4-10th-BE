package com.ktb10.kgb.tools.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpTourApiAreaClientTest {

    @Test
    void requestsWholeAreaListOnceWithConfiguredParameters() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://apis.data.go.kr/B551011/KorService2");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        byte[] response = "{\"response\":{}}".getBytes(StandardCharsets.UTF_8);
        server.expect(request -> {
            assertThat(request.getURI().getPath())
                    .isEqualTo("/B551011/KorService2/areaBasedList2");
            String query = request.getURI().getQuery();
            assertThat(query)
                    .contains("serviceKey=service-key")
                    .contains("MobileOS=ETC")
                    .contains("MobileApp=KGB")
                    .contains("_type=json")
                    .contains("pageNo=1")
                    .contains("numOfRows=60000");
        }).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        HttpTourApiAreaClient client = new HttpTourApiAreaClient(
                builder.build(),
                TourApiAreaSnapshotValidatorTest.properties("data/tourism/raw"));

        byte[] result = client.fetchSnapshot();

        assertThat(result).isEqualTo(response);
        server.verify();
    }

    @Test
    void marksServerErrorAsRetryable() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://apis.data.go.kr/B551011/KorService2");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> { }).andRespond(withServerError());
        HttpTourApiAreaClient client = new HttpTourApiAreaClient(
                builder.build(),
                TourApiAreaSnapshotValidatorTest.properties("data/tourism/raw"));

        assertThatThrownBy(client::fetchSnapshot)
                .isInstanceOf(TourApiCollectionException.class)
                .extracting(exception -> ((TourApiCollectionException) exception).isRetryable())
                .isEqualTo(true);
        server.verify();
    }
}
