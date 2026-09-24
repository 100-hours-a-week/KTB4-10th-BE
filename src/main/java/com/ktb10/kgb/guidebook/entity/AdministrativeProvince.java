package com.ktb10.kgb.guidebook.entity;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 행정안전부 행정구역 기준의 1단계 시도입니다. */
public enum AdministrativeProvince {
    SEOUL("11", "서울특별시"),
    JEONNAM_GWANGJU("12", "전남광주통합특별시"),
    BUSAN("26", "부산광역시"),
    DAEGU("27", "대구광역시"),
    INCHEON("28", "인천광역시"),
    DAEJEON("30", "대전광역시"),
    ULSAN("31", "울산광역시"),
    SEJONG("36", "세종특별자치시"),
    GYEONGGI("41", "경기도"),
    CHUNGBUK("43", "충청북도"),
    CHUNGNAM("44", "충청남도"),
    GYEONGBUK("47", "경상북도"),
    GYEONGNAM("48", "경상남도"),
    JEJU("50", "제주특별자치도"),
    GANGWON("51", "강원특별자치도"),
    JEONBUK("52", "전북특별자치도");

    private static final Map<String, AdministrativeProvince> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    AdministrativeProvince::code,
                    Function.identity()));
    private static final Map<String, AdministrativeProvince> BY_DISPLAY_NAME =
            Arrays.stream(values())
                    .collect(Collectors.toUnmodifiableMap(
                            AdministrativeProvince::displayName,
                            Function.identity()));

    private final String code;
    private final String displayName;

    AdministrativeProvince(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public static AdministrativeProvince fromCode(String code) {
        AdministrativeProvince province = BY_CODE.get(code);
        if (province == null) {
            throw new IllegalArgumentException("지원하지 않는 시도 코드입니다: " + code);
        }
        return province;
    }

    public static AdministrativeProvince fromDisplayName(String displayName) {
        AdministrativeProvince province = BY_DISPLAY_NAME.get(displayName);
        if (province == null) {
            throw new IllegalArgumentException("지원하지 않는 시도명입니다: " + displayName);
        }
        return province;
    }
}
