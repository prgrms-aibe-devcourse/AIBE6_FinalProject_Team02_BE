package com.backend_catcheat.domain.illustration.entity;

// 지시 문구를 서버가 들고 있어야 프롬프트를 고칠 때 클라이언트를 같이 배포하지 않는다
public enum RevisionPreset {

    SIMPLER("더 단순하게", "형태를 더 단순하게, 디테일을 더 줄여줘"),
    BRIGHTER("색을 밝게", "색을 더 밝고 선명하게 칠해줘"),
    BIGGER("더 크게", "대상을 화면에서 더 크게 그려줘"),
    MORE_CRAYON("크레파스 느낌 강하게", "크레파스 질감과 삐뚤한 선을 더 강하게 살려줘"),
    REMOVE_DISH("그릇 빼기", "그릇과 식기를 빼고 대상만 그려줘");

    private final String label;
    private final String instruction;

    RevisionPreset(String label, String instruction) {
        this.label = label;
        this.instruction = instruction;
    }

    public String label() {
        return label;
    }

    public String instruction() {
        return instruction;
    }
}
