package com.ktb10.kgb.credit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 회원의 생성권 잔액과 현재 생성 가능 상태입니다. */
public record CreditWalletResponse(
        @JsonProperty("credit_balance") int creditBalance,
        @JsonProperty("active_job_id") Long activeJobId,
        @JsonProperty("can_generate") boolean canGenerate) {
}
