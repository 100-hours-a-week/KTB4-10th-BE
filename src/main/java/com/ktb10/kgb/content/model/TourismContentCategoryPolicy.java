package com.ktb10.kgb.content.model;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** TourAPI 중분류를 서비스 장소 카테고리로 변환하는 화이트리스트 정책입니다. */
public enum TourismContentCategoryPolicy {
    NATURE_MOUNTAIN("NA01", "NATURE", "자연", 10, "NATURE_MOUNTAIN", "산", 10),
    NATURE_RIVER_SEA("NA02", "NATURE", "자연", 10, "NATURE_RIVER_SEA", "하천·해양", 20),
    NATURE_ECOLOGY("NA03", "NATURE", "자연", 10, "NATURE_ECOLOGY", "자연생태", 30),
    NATURE_PARK("NA04", "NATURE", "자연", 10, "NATURE_PARK", "공원", 40),
    NATURE_URBAN_PARK("VE03", "NATURE", "자연", 10, "NATURE_PARK", "공원", 40),
    NATURE_ETC("NA05", "NATURE", "자연", 10, "NATURE_ETC", "그 외", 50),

    HISTORY_HERITAGE_SITE(
            "HS01", "HISTORY", "역사", 20, "HISTORY_HERITAGE_SITE", "역사 유적지", 10),
    HISTORY_RELIC("HS02", "HISTORY", "역사", 20, "HISTORY_RELIC", "역사 유물", 20),
    HISTORY_RELIGIOUS_SITE(
            "HS03", "HISTORY", "역사", 20, "HISTORY_RELIGIOUS_SITE", "종교 성지", 30),
    HISTORY_SECURITY_SITE(
            "HS04", "HISTORY", "역사", 20, "HISTORY_SECURITY_SITE", "안보 관광지", 40),

    ATTRACTION_LANDMARK(
            "VE01", "ATTRACTION", "관광 명소", 30, "ATTRACTION_LANDMARK", "랜드마크", 10),
    ATTRACTION_THEME_PARK(
            "VE02", "ATTRACTION", "관광 명소", 30, "ATTRACTION_THEME_PARK", "테마 공원", 20),
    ATTRACTION_URBAN_CULTURE(
            "VE04", "ATTRACTION", "관광 명소", 30,
            "ATTRACTION_URBAN_CULTURE", "도시·지역 문화 관광", 30),
    ATTRACTION_EXHIBITION(
            "VE07", "ATTRACTION", "관광 명소", 30, "ATTRACTION_EXHIBITION", "전시 시설", 40),

    EXPERIENCE_TRADITION(
            "EX01", "EXPERIENCE", "체험", 40, "EXPERIENCE_TRADITION", "전통 체험", 10),
    EXPERIENCE_CRAFT("EX02", "EXPERIENCE", "체험", 40, "EXPERIENCE_CRAFT", "공예 체험", 20),
    EXPERIENCE_RURAL(
            "EX03", "EXPERIENCE", "체험", 40, "EXPERIENCE_RURAL", "농·산·어촌 체험", 30),
    EXPERIENCE_TEMPLE_STAY(
            "EX04", "EXPERIENCE", "체험", 40, "EXPERIENCE_TEMPLE_STAY", "산사 체험", 40),
    EXPERIENCE_HEALING(
            "EX05", "EXPERIENCE", "체험", 40, "EXPERIENCE_HEALING", "힐링 체험", 50),
    EXPERIENCE_INDUSTRY(
            "EX06", "EXPERIENCE", "체험", 40, "EXPERIENCE_INDUSTRY", "산업 관광", 60),
    EXPERIENCE_ETC("EX07", "EXPERIENCE", "체험", 40, "EXPERIENCE_ETC", "그 외 체험", 70),

    LEISURE_SPORTS_LAND(
            "LS01", "LEISURE_SPORTS", "레저 스포츠", 50, "LEISURE_SPORTS_LAND", "육상", 10),
    LEISURE_SPORTS_WATER(
            "LS02", "LEISURE_SPORTS", "레저 스포츠", 50, "LEISURE_SPORTS_WATER", "수상", 20),
    LEISURE_SPORTS_AIR(
            "LS03", "LEISURE_SPORTS", "레저 스포츠", 50, "LEISURE_SPORTS_AIR", "항공", 30),
    LEISURE_SPORTS_COMPLEX(
            "LS04", "LEISURE_SPORTS", "레저 스포츠", 50, "LEISURE_SPORTS_COMPLEX", "복합", 40);

    private static final Map<String, TourismContentCategoryPolicy> BY_SOURCE_CODE =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(
                    TourismContentCategoryPolicy::sourceCode,
                    Function.identity()));
    private static final List<String> ALLOWED_SOURCE_CODES =
            Arrays.stream(values()).map(TourismContentCategoryPolicy::sourceCode).toList();

    private final String sourceCode;
    private final Category theme;
    private final Category detail;

    TourismContentCategoryPolicy(
            String sourceCode,
            String themeCode,
            String themeLabel,
            int themeSortOrder,
            String detailCode,
            String detailLabel,
            int detailSortOrder) {
        this.sourceCode = sourceCode;
        this.theme = new Category(themeCode, themeLabel, themeSortOrder);
        this.detail = new Category(detailCode, detailLabel, detailSortOrder);
    }

    public static Optional<TourismContentCategoryPolicy> fromSourceCode(String sourceCode) {
        return Optional.ofNullable(BY_SOURCE_CODE.get(sourceCode));
    }

    public static List<String> allowedSourceCodes() {
        return ALLOWED_SOURCE_CODES;
    }

    public String sourceCode() {
        return sourceCode;
    }

    public Category theme() {
        return theme;
    }

    public Category detail() {
        return detail;
    }

    public record Category(String code, String label, int sortOrder) {
    }
}
