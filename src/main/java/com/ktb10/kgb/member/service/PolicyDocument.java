package com.ktb10.kgb.member.service;

import java.util.Arrays;
import java.util.Optional;

/** 공개 가능한 정책 코드와 정적 Markdown 리소스를 연결합니다. */
public enum PolicyDocument {
    TERMS("terms", "이용약관", "policies/terms-v1.md"),
    PRIVACY("privacy", "개인정보 처리방침", "policies/privacy-v1.md");

    private final String policyType;
    private final String title;
    private final String resourcePath;

    PolicyDocument(String policyType, String title, String resourcePath) {
        this.policyType = policyType;
        this.title = title;
        this.resourcePath = resourcePath;
    }

    public static Optional<PolicyDocument> findByPolicyType(String policyType) {
        return Arrays.stream(values())
                .filter(document -> document.policyType.equals(policyType))
                .findFirst();
    }

    public String policyType() {
        return policyType;
    }

    public String title() {
        return title;
    }

    public String resourcePath() {
        return resourcePath;
    }
}
