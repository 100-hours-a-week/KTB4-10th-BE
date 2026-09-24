package com.ktb10.kgb.guidebook.entity;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 행정안전부 행정구역 기준의 1단계 시도입니다. */
public enum AdministrativeProvince {
    SEOUL("서울특별시"),
    JEONNAM_GWANGJU("전남광주통합특별시"),
    BUSAN("부산광역시"),
    DAEGU("대구광역시"),
    INCHEON("인천광역시"),
    DAEJEON("대전광역시"),
    ULSAN("울산광역시"),
    SEJONG("세종특별자치시"),
    GYEONGGI("경기도"),
    CHUNGBUK("충청북도"),
    CHUNGNAM("충청남도"),
    GYEONGBUK("경상북도"),
    GYEONGNAM("경상남도"),
    JEJU("제주특별자치도"),
    GANGWON("강원특별자치도"),
    JEONBUK("전북특별자치도");

    private static final Map<String, AdministrativeProvince> BY_DISPLAY_NAME =
            Arrays.stream(values())
                    .collect(Collectors.toUnmodifiableMap(
                            AdministrativeProvince::displayName,
                            Function.identity()));

    private final String displayName;

    AdministrativeProvince(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static AdministrativeProvince fromDisplayName(String displayName) {
        AdministrativeProvince province = BY_DISPLAY_NAME.get(displayName);
        if (province == null) {
            throw new IllegalArgumentException("지원하지 않는 시도명입니다: " + displayName);
        }
        return province;
    }
}
