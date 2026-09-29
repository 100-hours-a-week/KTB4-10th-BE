package com.ktb10.kgb.credit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 쿠폰 등록으로 지된 생성권과 처리 후 잔액입니다. */
public record CouponRedeemResponse(
        @JsonProperty("granted_credits") int grantedCredits,
        @JsonProperty("credit_balance") int creditBalance) {
}