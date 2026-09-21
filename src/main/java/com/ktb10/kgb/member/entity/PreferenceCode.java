package com.ktb10.kgb.member.entity;

/** V1 취향 선택지의 안정 코드, 표시명, 부모와 표시 순서를 정의합니다. */
public enum PreferenceCode {
    NATURE(PreferenceType.THEME, "자연", null, 10),
    NATURE_MOUNTAIN(PreferenceType.DETAIL, "산", NATURE, 10),
    NATURE_RIVER_SEA(PreferenceType.DETAIL, "하천·해양", NATURE, 20),
    NATURE_ECOLOGY(PreferenceType.DETAIL, "자연생태", NATURE, 30),
    NATURE_PARK(PreferenceType.DETAIL, "공원", NATURE, 40),
    NATURE_ETC(PreferenceType.DETAIL, "그 외", NATURE, 50),

    HISTORY(PreferenceType.THEME, "역사", null, 20),
    HISTORY_HERITAGE_SITE(PreferenceType.DETAIL, "역사 유적지", HISTORY, 10),
    HISTORY_RELIC(PreferenceType.DETAIL, "역사 유물", HISTORY, 20),
    HISTORY_RELIGIOUS_SITE(PreferenceType.DETAIL, "종교 성지", HISTORY, 30),
    HISTORY_SECURITY_SITE(PreferenceType.DETAIL, "안보 관광지", HISTORY, 40),

    ATTRACTION(PreferenceType.THEME, "관광 명소", null, 30),
    ATTRACTION_LANDMARK(PreferenceType.DETAIL, "랜드마크", ATTRACTION, 10),
    ATTRACTION_THEME_PARK(PreferenceType.DETAIL, "테마 공원", ATTRACTION, 20),
    ATTRACTION_URBAN_CULTURE(
            PreferenceType.DETAIL,
            "도시·지역 문화 관광",
            ATTRACTION,
            30),
    ATTRACTION_EXHIBITION(PreferenceType.DETAIL, "전시 시설", ATTRACTION, 40),

    EXPERIENCE(PreferenceType.THEME, "체험", null, 40),
    EXPERIENCE_TRADITION(PreferenceType.DETAIL, "전통 체험", EXPERIENCE, 10),
    EXPERIENCE_CRAFT(PreferenceType.DETAIL, "공예 체험", EXPERIENCE, 20),
    EXPERIENCE_RURAL(PreferenceType.DETAIL, "농·산·어촌 체험", EXPERIENCE, 30),
    EXPERIENCE_TEMPLE_STAY(PreferenceType.DETAIL, "산사 체험", EXPERIENCE, 40),
    EXPERIENCE_HEALING(PreferenceType.DETAIL, "힐링 체험", EXPERIENCE, 50),
    EXPERIENCE_INDUSTRY(PreferenceType.DETAIL, "산업 관광", EXPERIENCE, 60),
    EXPERIENCE_ETC(PreferenceType.DETAIL, "그 외 체험", EXPERIENCE, 70),

    LEISURE_SPORTS(PreferenceType.THEME, "레저 스포츠", null, 50),
    LEISURE_SPORTS_LAND(PreferenceType.DETAIL, "육상", LEISURE_SPORTS, 10),
    LEISURE_SPORTS_WATER(PreferenceType.DETAIL, "수상", LEISURE_SPORTS, 20),
    LEISURE_SPORTS_AIR(PreferenceType.DETAIL, "항공", LEISURE_SPORTS, 30),
    LEISURE_SPORTS_COMPLEX(PreferenceType.DETAIL, "복합", LEISURE_SPORTS, 40),

    EVENTS(PreferenceType.THEME, "축제/공연/행사", null, 60),
    EVENTS_FESTIVAL(PreferenceType.DETAIL, "축제", EVENTS, 10),
    EVENTS_CONCERT(PreferenceType.DETAIL, "공연", EVENTS, 20),
    EVENTS_FAIR(PreferenceType.DETAIL, "행사", EVENTS, 30),

    RELAXING(PreferenceType.TRAVEL_STYLE, "여유롭게", null, 20),
    TIME_EFFICIENCY(PreferenceType.TRAVEL_STYLE, "효율적으로", null, 30),
    WALK_FRIENDLY(PreferenceType.TRAVEL_STYLE, "걷는거 좋아요", null, 40),
    CAR_TRAVEL(PreferenceType.TRAVEL_STYLE, "차로 이동", null, 50);

    private final PreferenceType type;
    private final String label;
    private final PreferenceCode parent;
    private final int sortOrder;

    PreferenceCode(
            PreferenceType type,
            String label,
            PreferenceCode parent,
            int sortOrder) {
        this.type = type;
        this.label = label;
        this.parent = parent;
        this.sortOrder = sortOrder;
    }

    public PreferenceType type() {
        return type;
    }

    public String label() {
        return label;
    }

    public String parentCode() {
        return parent == null ? null : parent.name();
    }

    public PreferenceCode parent() {
        return parent;
    }

    public int sortOrder() {
        return sortOrder;
    }
}
