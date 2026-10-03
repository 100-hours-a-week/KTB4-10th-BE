package com.ktb10.kgb.guidebook.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpGuidebookAiClientTest {

    private MockRestServiceServer server;
    private HttpGuidebookAiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://ai.internal:8000");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpGuidebookAiClient(builder.build());
    }

    @Test
    void requestsGenerationAndReadsAcceptedEnvelope() {
        server.expect(once(), requestTo("http://ai.internal:8000/guidebooks-generations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "request_id": "301-0",
                          "region": {"province": "경상북도", "city": "경주시"},
                          "start_date": "26.10.12",
                          "end_date": "26.10.14",
                          "companion": "couple",
                          "people_count": 2,
                          "preferences": {
                            "large_category": ["힐링"],
                            "mid_category": {"힐링": ["카페에서 쉬기"]},
                            "travel_style": ["여유롭게"]
                          },
                          "contents": []
                        }
                        """))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                        {
                          "message": "guidebook_accepted",
                          "data": {"job_id": "job_12345", "status": "pending"}
                        }
                        """));

        var response = client.requestGeneration(request());

        assertThat(response.jobId()).isEqualTo("job_12345");
        assertThat(response.status()).isEqualTo(AiGenerationStatus.PENDING);
        server.verify();
    }

    @Test
    void readsCompletedGenerationStatus() {
        server.expect(once(), requestTo(
                        "http://ai.internal:8000/guidebooks-generations/job_12345"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "message": "guidebook_completed",
                          "data": {
                            "job_id": "job_12345",
                            "status": "completed",
                            "result": {
                              "title": "경주 역사 여행",
                              "summary": "여행 요약",
                              "content_html": "<article>가이드북</article>",
                              "itinerary": [{
                                "day": 1,
                                "date": "26.10.12",
                                "day_summary": "첫날 일정",
                                "places": [{
                                  "order": 1,
                                  "time": "09:00",
                                  "content_id": "126508",
                                  "duration_minutes": 60,
                                  "recommend_reason": "추천 이유"
                                }]
                              }]
                            },
                            "error": null
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.getGenerationStatus("job_12345");

        assertThat(response.status()).isEqualTo(AiGenerationStatus.COMPLETED);
        assertThat(response.result().contentHtml())
                .isEqualTo("<article>가이드북</article>");
        assertThat(response.result().itinerary().get(0).daySummary())
                .isEqualTo("첫날 일정");
        server.verify();
    }

    @Test
    void rejectsDifferentJobIdInStatusResponse() {
        server.expect(requestTo(
                        "http://ai.internal:8000/guidebooks-generations/job_12345"))
                .andRespond(withSuccess("""
                        {
                          "message": "guidebook_processing",
                          "data": {"job_id": "different_job", "status": "processing"}
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getGenerationStatus("job_12345"))
                .isInstanceOf(AiClientException.class)
                .hasMessage("AI 상태 조회 응답 형식이 올바르지 않습니다.");
        server.verify();
    }

    @Test
    void convertsAiErrorResponseToClientException() {
        server.expect(requestTo(
                        "http://ai.internal:8000/guidebooks-generations/unknown_job"))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.getGenerationStatus("unknown_job"))
                .isInstanceOf(AiClientException.class)
                .hasMessage("AI 서버가 오류 응답을 반환했습니다. status=404")
                .extracting(exception -> ((AiClientException) exception).getFailureType())
                .isEqualTo(AiFailureType.UPSTREAM_4XX);
        server.verify();
    }

    @Test
    void convertsConnectionFailureToClientException() {
        server.expect(requestTo("http://ai.internal:8000/guidebooks-generations"))
                .andRespond(request -> {
                    throw new IOException("connection reset");
                });

        assertThatThrownBy(() -> client.requestGeneration(request()))
                .isInstanceOf(AiClientException.class)
                .hasMessage("AI 서버 요청에 실패했습니다.")
                .extracting(exception -> ((AiClientException) exception).getFailureType())
                .isEqualTo(AiFailureType.CONNECTION_ERROR);
        server.verify();
    }

    @Test
    void classifiesReadTimeout() {
        server.expect(requestTo("http://ai.internal:8000/guidebooks-generations"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        assertThatThrownBy(() -> client.requestGeneration(request()))
                .isInstanceOf(AiClientException.class)
                .extracting(exception -> ((AiClientException) exception).getFailureType())
                .isEqualTo(AiFailureType.READ_TIMEOUT);
        server.verify();
    }

    @Test
    void classifiesConnectTimeout() {
        server.expect(requestTo("http://ai.internal:8000/guidebooks-generations"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("connect timed out");
                });

        assertThatThrownBy(() -> client.requestGeneration(request()))
                .isInstanceOf(AiClientException.class)
                .extracting(exception -> ((AiClientException) exception).getFailureType())
                .isEqualTo(AiFailureType.CONNECT_TIMEOUT);
        server.verify();
    }

    @Test
    void classifiesUpstreamServerError() {
        server.expect(requestTo("http://ai.internal:8000/guidebooks-generations"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.requestGeneration(request()))
                .isInstanceOf(AiClientException.class)
                .satisfies(exception -> {
                    AiClientException aiException = (AiClientException) exception;
                    assertThat(aiException.getFailureType())
                            .isEqualTo(AiFailureType.UPSTREAM_5XX);
                    assertThat(aiException.getUpstreamStatus()).isEqualTo(502);
                    assertThat(aiException.getTargetRoute())
                            .isEqualTo("/guidebooks-generations");
                });
        server.verify();
    }

    @Test
    void classifiesUnexpectedClientError() {
        server.expect(requestTo("http://ai.internal:8000/guidebooks-generations"))
                .andRespond(request -> {
                    throw new IllegalStateException("unexpected client failure");
                });

        assertThatThrownBy(() -> client.requestGeneration(request()))
                .isInstanceOf(AiClientException.class)
                .extracting(exception -> ((AiClientException) exception).getFailureType())
                .isEqualTo(AiFailureType.UNEXPECTED_ERROR);
        server.verify();
    }

    @Test
    void rejectsMalformedResponseBody() {
        server.expect(requestTo("http://ai.internal:8000/guidebooks-generations"))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"guidebook_accepted\",\"data\":null}"));

        assertThatThrownBy(() -> client.requestGeneration(request()))
                .isInstanceOf(AiClientException.class)
                .hasMessage("AI 서버 응답 형식이 올바르지 않습니다.")
                .extracting(exception -> ((AiClientException) exception).getFailureType())
                .isEqualTo(AiFailureType.INVALID_RESPONSE);
        server.verify();
    }

    private AiGuidebookRequest request() {
        return new AiGuidebookRequest(
                "301-0",
                new AiGuidebookRequest.Region("경상북도", "경주시"),
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 14),
                "couple",
                2,
                new AiGuidebookRequest.Preferences(
                        List.of("힐링"),
                        Map.of("힐링", List.of("카페에서 쉬기")),
                        List.of("여유롭게")),
                List.of());
    }
}
