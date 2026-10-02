package com.ktb10.kgb.tools.tourapi;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 공공데이터포털 TourAPI의 전국 목록을 한 번에 조회합니다. */
public class HttpTourApiAreaClient implements TourApiAreaClient {

    private static final String AREA_PATH = "areaBasedList2";

    private final RestClient restClient;
    private final TourApiCollectionProperties properties;

    public HttpTourApiAreaClient(
            RestClient restClient,
            TourApiCollectionProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public byte[] fetchSnapshot() {
        if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
            throw new TourApiCollectionException(
                    "TOUR_API_SERVICE_KEY가 설정되지 않았습니다.",
                    false);
        }
        try {
            byte[] body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment(AREA_PATH)
                            .queryParam("serviceKey", properties.serviceKey())
                            .queryParam("MobileOS", properties.mobileOs())
                            .queryParam("MobileApp", properties.mobileApp())
                            .queryParam("_type", "json")
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", properties.numOfRows())
                            .build())
                    .retrieve()
                    .body(byte[].class);
            if (body == null || body.length == 0) {
                throw new TourApiCollectionException(
                        "TourAPI 응답 본문이 비어 있습니다.",
                        true);
            }
            return body;
        } catch (TourApiCollectionException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            boolean retryable = status.value() == 429 || status.is5xxServerError();
            throw new TourApiCollectionException(
                    "TourAPI 오류 응답: status=" + status.value(),
                    retryable,
                    exception);
        } catch (RestClientException exception) {
            throw new TourApiCollectionException(
                    "TourAPI 요청에 실패했습니다.",
                    true,
                    exception);
        }
    }
}
