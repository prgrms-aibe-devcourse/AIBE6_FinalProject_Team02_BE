package com.backend_catcheat.domain.dex.basicdex.type;

public enum Category {
    // 밥, 죽, 한그릇
    RICE_DISH("밥·죽·한그릇"),

    // 면
    NOODLE("면"),

    // 국, 탕, 찌개
    SOUP_STEW("국·탕·찌개"),

    // 고기, 구이, 볶음
    MEAT_DISH("고기·구이·볶음"),

    // 튀김, 치킨, 가스
    FRIED_CHICKEN_CUTLET("튀김·치킨·가스"),

    // 해산물, 회
    SEAFOOD("해산물·회"),

    // 분식, 길거리
    STREET_FOOD("분식·길거리"),

    // 빵, 버거, 피자, 브런치
    BREAD_BURGER_PIZZA_BRUNCH("빵·버거·피자·브런치"),

    // 디저트, 음료
    DESSERT_DRINK("디저트·음료");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
