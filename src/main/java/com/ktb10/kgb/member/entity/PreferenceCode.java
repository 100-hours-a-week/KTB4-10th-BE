package com.ktb10.kgb.member.entity;

/** V1에서 사용하는 취향 코드와 중분류의 부모 대분류 코드입니다. */
public enum PreferenceCode {
    HEALING(PreferenceType.THEME, null),
    QUIET_PLACE(PreferenceType.DETAIL, "HEALING"),
    HEALING_SPA(PreferenceType.DETAIL, "HEALING"),
    HEALING_CAFE_REST(PreferenceType.DETAIL, "HEALING"),
    HEALING_FOREST_WALK(PreferenceType.DETAIL, "HEALING"),
    HEALING_SEA_VIEW(PreferenceType.DETAIL, "HEALING"),
    HEALING_LAKE_REST(PreferenceType.DETAIL, "HEALING"),

    ACTIVITY(PreferenceType.THEME, null),
    ACTIVITY_HIKING(PreferenceType.DETAIL, "ACTIVITY"),
    ACTIVITY_WATER_SPORTS(PreferenceType.DETAIL, "ACTIVITY"),
    ACTIVITY_CYCLING(PreferenceType.DETAIL, "ACTIVITY"),
    ACTIVITY_LEISURE_SPORTS(PreferenceType.DETAIL, "ACTIVITY"),
    ACTIVITY_CAMPING(PreferenceType.DETAIL, "ACTIVITY"),
    ACTIVITY_THEME_PARK(PreferenceType.DETAIL, "ACTIVITY"),

    FOOD(PreferenceType.THEME, null),
    FOOD_LOCAL_FOOD(PreferenceType.DETAIL, "FOOD"),
    FOOD_TRADITIONAL_MARKET(PreferenceType.DETAIL, "FOOD"),
    FOOD_SEAFOOD(PreferenceType.DETAIL, "FOOD"),
    FOOD_DESSERT(PreferenceType.DETAIL, "FOOD"),
    FOOD_LOCAL_DRINK(PreferenceType.DETAIL, "FOOD"),
    FOOD_FOOD_EXPERIENCE(PreferenceType.DETAIL, "FOOD"),

    CULTURE(PreferenceType.THEME, null),
    CULTURE_HISTORY(PreferenceType.DETAIL, "CULTURE"),
    CULTURE_MUSEUM(PreferenceType.DETAIL, "CULTURE"),
    CULTURE_GALLERY(PreferenceType.DETAIL, "CULTURE"),
    CULTURE_TRADITIONAL_CULTURE(PreferenceType.DETAIL, "CULTURE"),
    CULTURE_PERFORMANCE(PreferenceType.DETAIL, "CULTURE"),
    CULTURE_ARCHITECTURE(PreferenceType.DETAIL, "CULTURE"),

    NATURE(PreferenceType.THEME, null),
    NATURE_MOUNTAIN(PreferenceType.DETAIL, "NATURE"),
    NATURE_BEACH(PreferenceType.DETAIL, "NATURE"),
    NATURE_FOREST(PreferenceType.DETAIL, "NATURE"),
    NATURE_GARDEN(PreferenceType.DETAIL, "NATURE"),
    NATURE_RIVER_LAKE(PreferenceType.DETAIL, "NATURE"),
    NATURE_ECOLOGY(PreferenceType.DETAIL, "NATURE"),

    SHOPPING(PreferenceType.THEME, null),
    SHOPPING_SOUVENIR(PreferenceType.DETAIL, "SHOPPING"),
    SHOPPING_LOCAL_CRAFT(PreferenceType.DETAIL, "SHOPPING"),
    SHOPPING_MARKET(PreferenceType.DETAIL, "SHOPPING"),
    SHOPPING_SELECT_SHOP(PreferenceType.DETAIL, "SHOPPING"),
    SHOPPING_OUTLET(PreferenceType.DETAIL, "SHOPPING"),
    SHOPPING_LOCAL_BRAND(PreferenceType.DETAIL, "SHOPPING"),

    AVOID_CROWDS(PreferenceType.TRAVEL_STYLE, null),
    WALK_FRIENDLY(PreferenceType.TRAVEL_STYLE, null),
    CAR_TRAVEL(PreferenceType.TRAVEL_STYLE, null),
    MORNING_PERSON(PreferenceType.TRAVEL_STYLE, null),
    RELAXED(PreferenceType.TRAVEL_STYLE, null),
    WITH_CHILDREN(PreferenceType.TRAVEL_STYLE, null),
    BUDGET_FRIENDLY(PreferenceType.TRAVEL_STYLE, null),
    PHOTO_FOCUSED(PreferenceType.TRAVEL_STYLE, null);

    private final PreferenceType type;
    private final String parentCode;

    PreferenceCode(PreferenceType type, String parentCode) {
        this.type = type;
        this.parentCode = parentCode;
    }

    public PreferenceType type() {
        return type;
    }

    public String parentCode() {
        return parentCode;
    }
}
