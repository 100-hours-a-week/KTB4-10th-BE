package com.ktb10.kgb.tools.tourapi;

/** TourAPI 수집 단계의 재시도 가능 여부를 함께 전달합니다. */
public class TourApiCollectionException extends RuntimeException {

    private final boolean retryable;

    public TourApiCollectionException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public TourApiCollectionException(
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
