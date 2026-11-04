package com.ktb10.kgb.guidebook.entity;

public enum GenerationStatus {
    PENDING,
    PUBLISH_PENDING,
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELED;

    public GenerationStatus toPublicStatus() {
        return isWaiting() ? PENDING : this;
    }

    public boolean isWaiting() {
        return this == PENDING || this == PUBLISH_PENDING || this == QUEUED;
    }

    public boolean isActive() {
        return isWaiting() || this == PROCESSING;
    }
}
