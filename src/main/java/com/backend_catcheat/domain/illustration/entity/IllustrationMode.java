package com.backend_catcheat.domain.illustration.entity;

public enum IllustrationMode {

    // 사진 변환. 원본이 구도·색·형태를 고정해 준다
    STYLIZE,
    // 설명만으로 생성. 보상 뱃지는 해당하는 사진이 유저에게 없어 이 경로가 필요하다
    GENERATE
}
