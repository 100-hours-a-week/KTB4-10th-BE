package com.ktb10.kgb.content.tourapi;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

/** 공공데이터포털 TourAPI를 HTTP로 호출합니다. */
public class HttpTourApiClient implements TourApiClient {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final TourApiProperties properties;

    public HttpTourApiClient(RestClient restClient, TourApiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public byte[] fetchAreaSnapshot() {
        return fetch(uriBuilder -> commonParameters(uriBuilder.pathSegment("areaBasedList2"))
                .build());
    }

    @Override
    public byte[] fetchFestivalSnapshot(LocalDate startDate, LocalDate endDate) {
        return fetch(uriBuilder -> commonParameters(uriBuilder.pathSegment("searchFestival2"))
                .queryParam("eventStartDate", startDate.format(DATE))
                .queryParam("eventEndDate", endDate.format(DATE))
                .build());
    }

    private UriBuilder commonParameters(UriBuilder uriBuilder) {
        return uriBuilder
                .queryParam("serviceKey", properties.serviceKey())
                .queryParam("MobileOS", properties.mobileOs())
                .queryParam("MobileApp", properties.mobileApp())
                .queryParam("_type", "json")
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", properties.numOfRows());
    }

    private byte[] fetch(Function<UriBuilder, URI> uriFactory) {
        if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
            throw new TourApiException("TOUR_API_SERVICE_KEY가 설정되지 않았습니다.", false);
        }
        try {
            byte[] body = restClient.get()
                    .uri(uriFactory)
                    .retrieve()
                    .body(byte[].class);
            if (body == null || body.length == 0) {
                throw new TourApiException("TourAPI 응답 본문이 비어 있습니다.", true);
            }
            return body;
        } catch (TourApiException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            boolean retryable = status.value() == 429 || status.is5xxServerError();
            throw new TourApiException(
                    "TourAPI 오류 응답: status=" + status.value(), retryable, exception);
        } catch (RestClientException exception) {
            throw new TourApiException("TourAPI 요청에 실패했습니다.", true, exception);
        }
    }
}
