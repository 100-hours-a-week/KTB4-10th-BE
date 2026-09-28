package com.ktb10.kgb.guidebook.client;

import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedEnvelope;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusEnvelope;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 실제 AI 서버의 비동기 가이드북 생성 API를 호출합니다. */
public class HttpGuidebookAiClient implements GuidebookAiClient {

    private static final String GENERATIONS_PATH = "/guidebooks-generations";

    private final RestClient restClient;

    public HttpGuidebookAiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public AiGenerationAcceptedResponse requestGeneration(AiGuidebookRequest request) {
        try {
            ResponseEntity<AiGenerationAcceptedEnvelope> response = restClient.post()
                    .uri(GENERATIONS_PATH)
                    .body(request)
                    .retrieve()
                    .toEntity(AiGenerationAcceptedEnvelope.class);
            if (response.getStatusCode() != HttpStatus.ACCEPTED) {
                throw new AiClientException("AI 생성 접수 응답 상태가 올바르지 않습니다.");
            }
            AiGenerationAcceptedEnvelope envelope = Objects.requireNonNull(
                    response.getBody(), "AI 생성 접수 응답 본문이 없습니다.");
            AiGenerationAcceptedResponse data = envelope.data();
            if (data.jobId() == null || data.jobId().isBlank() || data.status() == null) {
                throw new AiClientException("AI 생성 접수 응답 형식이 올바르지 않습니다.");
            }
            return data;
        } catch (AiClientException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw new AiClientException(
                    "AI 서버가 오류 응답을 반환했습니다. status="
                            + exception.getStatusCode().value(),
                    exception);
        } catch (RestClientException | IllegalArgumentException | NullPointerException exception) {
            throw new AiClientException("AI 서버 생성 접수 요청에 실패했습니다.", exception);
        }
    }

    @Override
    public AiGenerationStatusResponse getGenerationStatus(String aiJobId) {
        try {
            ResponseEntity<AiGenerationStatusEnvelope> response = restClient.get()
                    .uri(GENERATIONS_PATH + "/{jobId}", aiJobId)
                    .retrieve()
                    .toEntity(AiGenerationStatusEnvelope.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new AiClientException("AI 상태 조회 응답 상태가 올바르지 않습니다.");
            }
            AiGenerationStatusEnvelope envelope = Objects.requireNonNull(
                    response.getBody(), "AI 상태 조회 응답 본문이 없습니다.");
            AiGenerationStatusResponse data = envelope.data();
            if (!aiJobId.equals(data.jobId()) || data.status() == null) {
                throw new AiClientException("AI 상태 조회 응답 형식이 올바르지 않습니다.");
            }
            return data;
        } catch (AiClientException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw new AiClientException(
                    "AI 서버가 오류 응답을 반환했습니다. status="
                            + exception.getStatusCode().value(),
                    exception);
        } catch (RestClientException | IllegalArgumentException | NullPointerException exception) {
            throw new AiClientException("AI 서버 상태 조회 요청에 실패했습니다.", exception);
        }
    }
}
