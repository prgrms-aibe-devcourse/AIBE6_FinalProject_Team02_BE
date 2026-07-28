package com.backend_catcheat.domain.dex.basicdex.type;

public enum Category {
    // 밥, 죽, 한그릇
    RICE_DISH("밥·죽·한 그릇", "밥,죽, 한그릇"),

    // 면
    NOODLE("면", "면"),

    // 국, 탕, 찌개
    SOUP_STEW("국·탕·찌개", "국-탕-찌개"),

    // 고기, 구이, 볶음
    MEAT_DISH("고기 구이·볶음", "고기 구이 및 볶음"),

    // 튀김, 치킨, 까스
    FRIED_CHICKEN_CUTLET("튀김·치킨·까스", "튀김, 치킨, 까스"),

    // 해산물, 회
    SEAFOOD("해산물·회", "해산물"),

    // 분식, 길거리
    STREET_FOOD("분식·길거리", "분식·길거리"),

    // 빵, 버거, 피자, 브런치
    BREAD_BURGER_PIZZA_BRUNCH("빵·버거·피자·브런치", "빵, 버거, 피자, 브런치"),

    // 디저트, 음료
    // 폴더명은 S3에 실제로 올라간 이름(`디저트/`)을 따른다 — displayName과 달라도 맞춰선 안 된다.
    DESSERT_DRINK("디저트·음료", "디저트");

    private final String displayName;
    private final String illustrationFolderName;

    Category(String displayName, String illustrationFolderName) {
        this.displayName = displayName;
        this.illustrationFolderName = illustrationFolderName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIllustrationFolderName() {
        return illustrationFolderName;
    }
}
