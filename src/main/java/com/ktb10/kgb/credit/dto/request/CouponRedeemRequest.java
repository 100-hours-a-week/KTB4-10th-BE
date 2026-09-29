package com.ktb10.kgb.credit.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 생성권 충전을 지겹할 쿠폰 입력입니다. */
public record CouponRedeemRequest(
        @JsonProperty("coupon_code")
        @NotBlank @Size(max = 100) String couponCode) {
}
