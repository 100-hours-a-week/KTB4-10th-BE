package com.ktb10.kgb.guidebook.client;

/** AI 작업을 찾을 수 없거나 AI Client 계약을 수행할 수 없을 때 발생합니다. */
public class AiClientException extends RuntimeException {

    private final AiFailureType failureType;
    private final String targetRoute;
    private final Integer upstreamStatus;
    private final long durationMs;

    public AiClientException(
            String message,
            Throwable cause,
            AiFailureType failureType,
            String targetRoute,
            Integer upstreamStatus,
            long durationMs) {
        super(message, cause);
        this.failureType = failureType;
        this.targetRoute = targetRoute;
        this.upstreamStatus = upstreamStatus;
        this.durationMs = durationMs;
    }

    public AiFailureType getFailureType() {
        return failureType;
    }

    public String getTargetRoute() {
        return targetRoute;
    }

    public Integer getUpstreamStatus() {
        return upstreamStatus;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
