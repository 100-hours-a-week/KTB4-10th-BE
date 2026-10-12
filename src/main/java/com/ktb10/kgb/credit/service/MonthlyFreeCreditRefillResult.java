package com.ktb10.kgb.credit.service;

/** 회원별 월 무료 생성권 보충 처리 결과입니다. */
public enum MonthlyFreeCreditRefillResult {
    REFILLED,
    NO_BALANCE_CHANGE,
    ALREADY_PROCESSED,
    MEMBER_NOT_ELIGIBLE
}
