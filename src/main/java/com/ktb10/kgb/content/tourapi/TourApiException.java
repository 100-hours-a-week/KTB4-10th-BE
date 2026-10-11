package com.ktb10.kgb.content.tourapi;

/** TourAPI 수집 단계의 재시도 가능 여부를 함께 전달합니다. */
public class TourApiException extends RuntimeException {

    private final boolean retryable;

    public TourApiException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public TourApiException(
            String message,
            boolean retryable,
            Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
