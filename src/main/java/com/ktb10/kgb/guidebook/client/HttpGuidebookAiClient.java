package com.ktb10.kgb.guidebook.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedEnvelope;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusEnvelope;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 실제 AI 서버의 비동기 가이드북 생성 API를 호출합니다. */
public class HttpGuidebookAiClient implements GuidebookAiClient {

    private static final String GENERATIONS_PATH = "/guidebooks-generations";
    private static final String GENERATION_STATUS_ROUTE = GENERATIONS_PATH + "/{jobId}";

    private final RestClient restClient;

    public HttpGuidebookAiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public AiGenerationAcceptedResponse requestGeneration(AiGuidebookRequest request) {
        long startedAt = System.nanoTime();
        try {
            ResponseEntity<AiGenerationAcceptedEnvelope> response = restClient.post()
                    .uri(GENERATIONS_PATH)
                    .body(request)
                    .retrieve()
                    .toEntity(AiGenerationAcceptedEnvelope.class);
            if (response.getStatusCode() != HttpStatus.ACCEPTED) {
                throw invalidResponse(
                        "AI 생성 접수 응답 상태가 올바르지 않습니다.",
                        GENERATIONS_PATH,
                        startedAt,
                        null);
            }
            AiGenerationAcceptedEnvelope envelope = Objects.requireNonNull(
                    response.getBody(), "AI 생성 접수 응답 본문이 없습니다.");
            AiGenerationAcceptedResponse data = envelope.data();
            if (data.jobId() == null || data.jobId().isBlank() || data.status() == null) {
                throw invalidResponse(
                        "AI 생성 접수 응답 형식이 올바르지 않습니다.",
                        GENERATIONS_PATH,
                        startedAt,
                        null);
            }
            if (data.status() != AiGenerationStatus.PENDING
                    && data.status() != AiGenerationStatus.PROCESSING) {
                throw invalidResponse(
                        "AI 생성 접수 상태가 올바르지 않습니다.",
                        GENERATIONS_PATH,
                        startedAt,
                        null);
            }
            return data;
        } catch (AiClientException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw upstreamError(exception, GENERATIONS_PATH, startedAt);
        } catch (RestClientException exception) {
            throw restClientError(exception, GENERATIONS_PATH, startedAt);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidResponse(
                    "AI 생성 접수 응답 형식이 올바르지 않습니다.",
                    GENERATIONS_PATH,
                    startedAt,
                    exception);
        } catch (RuntimeException exception) {
            throw unexpectedError(exception, GENERATIONS_PATH, startedAt);
        }
    }

    @Override
    public AiGenerationStatusResponse getGenerationStatus(String aiJobId) {
        long startedAt = System.nanoTime();
        try {
            ResponseEntity<AiGenerationStatusEnvelope> response = restClient.get()
                    .uri(GENERATIONS_PATH + "/{jobId}", aiJobId)
                    .retrieve()
                    .toEntity(AiGenerationStatusEnvelope.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw invalidResponse(
                        "AI 상태 조회 응답 상태가 올바르지 않습니다.",
                        GENERATION_STATUS_ROUTE,
                        startedAt,
                        null);
            }
            AiGenerationStatusEnvelope envelope = Objects.requireNonNull(
                    response.getBody(), "AI 상태 조회 응답 본문이 없습니다.");
            AiGenerationStatusResponse data = envelope.data();
            if (!aiJobId.equals(data.jobId()) || data.status() == null) {
                throw invalidResponse(
                        "AI 상태 조회 응답 형식이 올바르지 않습니다.",
                        GENERATION_STATUS_ROUTE,
                        startedAt,
                        null);
            }
            return data;
        } catch (AiClientException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw upstreamError(exception, GENERATION_STATUS_ROUTE, startedAt);
        } catch (RestClientException exception) {
            throw restClientError(exception, GENERATION_STATUS_ROUTE, startedAt);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidResponse(
                    "AI 상태 조회 응답 형식이 올바르지 않습니다.",
                    GENERATION_STATUS_ROUTE,
                    startedAt,
                    exception);
        } catch (RuntimeException exception) {
            throw unexpectedError(exception, GENERATION_STATUS_ROUTE, startedAt);
        }
    }

    private AiClientException upstreamError(
            RestClientResponseException exception,
            String route,
            long startedAt) {
        int status = exception.getStatusCode().value();
        AiFailureType failureType = exception.getStatusCode().is4xxClientError()
                ? AiFailureType.UPSTREAM_4XX
                : AiFailureType.UPSTREAM_5XX;
        return new AiClientException(
                "AI 서버가 오류 응답을 반환했습니다. status=" + status,
                exception,
                failureType,
                route,
                status,
                elapsedMillis(startedAt));
    }

    private AiClientException restClientError(
            RestClientException exception,
            String route,
            long startedAt) {
        AiFailureType failureType = classifyRestClientFailure(exception);
        String message = failureType == AiFailureType.INVALID_RESPONSE
                ? "AI 서버 응답 형식이 올바르지 않습니다."
                : "AI 서버 요청에 실패했습니다.";
        return new AiClientException(
                message,
                exception,
                failureType,
                route,
                null,
                elapsedMillis(startedAt));
    }

    private AiClientException invalidResponse(
            String message,
            String route,
            long startedAt,
            Throwable cause) {
        return new AiClientException(
                message,
                cause,
                AiFailureType.INVALID_RESPONSE,
                route,
                null,
                elapsedMillis(startedAt));
    }

    private AiClientException unexpectedError(
            RuntimeException exception,
            String route,
            long startedAt) {
        return new AiClientException(
                "AI 서버 요청 처리 중 예상하지 못한 오류가 발생했습니다.",
                exception,
                AiFailureType.UNEXPECTED_ERROR,
                route,
                null,
                elapsedMillis(startedAt));
    }

    private AiFailureType classifyRestClientFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof HttpConnectTimeoutException) {
                return AiFailureType.CONNECT_TIMEOUT;
            }
            if (current instanceof SocketTimeoutException socketTimeoutException) {
                String message = socketTimeoutException.getMessage();
                return message != null && message.toLowerCase().contains("connect")
                        ? AiFailureType.CONNECT_TIMEOUT
                        : AiFailureType.READ_TIMEOUT;
            }
            if (current instanceof HttpTimeoutException) {
                return AiFailureType.READ_TIMEOUT;
            }
            if (current instanceof HttpMessageConversionException
                    || current instanceof JsonProcessingException) {
                return AiFailureType.INVALID_RESPONSE;
            }
            if (current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof NoRouteToHostException) {
                return AiFailureType.CONNECTION_ERROR;
            }
            current = current.getCause();
        }
        return AiFailureType.CONNECTION_ERROR;
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
