package com.ktb10.kgb.guidebook.client;

/** AI 작업을 찾을 수 없거나 AI Client 계약을 수행할 수 없을 때 발생합니다. */
public class AiClientException extends RuntimeException {

    public AiClientException(String message) {
        super(message);
    }
}
