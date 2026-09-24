package com.ktb10.kgb.member.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.member.dto.response.PolicyListResponse;
import com.ktb10.kgb.member.dto.response.PolicyResponse;
import com.ktb10.kgb.member.dto.response.PolicySummaryResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** 배포 파일에 포함된 공개 정책 Markdown을 제공합니다. */
@Service
public class PolicyService {

    private static final String POLICY_FORMAT = "MARKDOWN";

    private final Map<PolicyDocument, String> policyContents;

    public PolicyService() {
        EnumMap<PolicyDocument, String> contents = new EnumMap<>(PolicyDocument.class);
        for (PolicyDocument document : PolicyDocument.values()) {
            contents.put(document, readContent(document));
        }
        this.policyContents = Map.copyOf(contents);
    }

    public PolicyListResponse getPolicies() {
        return new PolicyListResponse(java.util.Arrays.stream(PolicyDocument.values())
                .map(document -> new PolicySummaryResponse(
                        document.policyType(),
                        document.title()))
                .toList());
    }

    public PolicyResponse getPolicy(String policyType) {
        PolicyDocument document = PolicyDocument.findByPolicyType(policyType)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        return new PolicyResponse(
                document.policyType(),
                document.title(),
                POLICY_FORMAT,
                policyContents.get(document));
    }

    private static String readContent(PolicyDocument document) {
        ClassPathResource resource = new ClassPathResource(document.resourcePath());
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "정책 문서를 읽을 수 없습니다: " + document.policyType(),
                    exception);
        }
    }
}
