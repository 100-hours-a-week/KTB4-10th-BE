package com.ktb10.kgb.credit.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.credit.dto.request.CouponRedeemRequest;
import com.ktb10.kgb.credit.dto.response.CouponRedeemResponse;
import com.ktb10.kgb.credit.service.CouponRedemptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 생성권 쿠폰 유스케이스를 제공합니다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/credits")
public class CreditController {

    private final CouponRedemptionService couponRedemptionService;

    @PostMapping("/coupons/redeem")
    public ApiResponse<CouponRedeemResponse> redeemCoupon(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody CouponRedeemRequest request) {
        return ApiResponse.success(
                "credit_coupon_redeem_success",
                couponRedemptionService.redeem(member.memberId(), request.couponCode()));
    }
}
