package com.ktb10.kgb.common.security;

/** 원문 쿠키가 아닌 SHA-256 소문자 hex 연결값만 허용합니다. */
public final class SessionHash {

    private SessionHash() {
    }

    public static String requireValid(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid service session hash");
        }
        return value;
    }
}
