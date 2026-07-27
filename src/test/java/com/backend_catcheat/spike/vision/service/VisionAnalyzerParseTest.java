package com.backend_catcheat.spike.vision.service;

import com.backend_catcheat.spike.vision.config.VisionSpikeProperties;
import com.backend_catcheat.spike.vision.dto.ai.AiVerificationResult;
import com.backend_catcheat.spike.vision.exception.VisionSpikeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("AI 검증 응답 파싱")
class VisionAnalyzerParseTest {

    private VisionAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new VisionAnalyzer(
                mock(ChatModel.class),
                new ObjectMapper(),
                new VisionSpikeProperties(5, 1024, 0.8f, 3_500_000L, 5, true, "none"),
                "dummy-key"
        );
    }

    @Test
    @DisplayName("일치 판정을 파싱한다")
    void 일치_판정을_파싱한다() {
        AiVerificationResult result = analyzer.parse("""
                {"verdicts":[{"name":"김밥","matched":true,"confidence":0.95,"reason":""}]}""");

        assertThat(result.verdicts()).hasSize(1);
        assertThat(result.verdicts().getFirst().name()).isEqualTo("김밥");
        assertThat(result.verdicts().getFirst().matched()).isTrue();
        assertThat(result.verdicts().getFirst().confidence()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("불일치 판정은 사유를 함께 파싱한다")
    void 불일치_사유를_파싱한다() {
        AiVerificationResult result = analyzer.parse("""
                {"verdicts":[{"name":"삼겹살","matched":false,"confidence":0.98,"reason":"사진은 김밥으로 보여요"}]}""");

        assertThat(result.verdicts().getFirst().matched()).isFalse();
        assertThat(result.verdicts().getFirst().reason()).isEqualTo("사진은 김밥으로 보여요");
    }

    @Test
    @DisplayName("한 상 사진의 복수 판정을 파싱한다")
    void 복수_판정을_파싱한다() {
        AiVerificationResult result = analyzer.parse("""
                {"verdicts":[
                  {"name":"삼겹살","matched":true,"confidence":0.93,"reason":""},
                  {"name":"비빔냉면","matched":true,"confidence":0.88,"reason":""},
                  {"name":"된장찌개","matched":false,"confidence":0.7,"reason":"국물이 보이지 않아요"}
                ]}""");

        assertThat(result.verdicts()).hasSize(3);
        assertThat(result.verdicts()).extracting(AiVerificationResult.AiVerdict::matched)
                .containsExactly(true, true, false);
    }

    @Test
    @DisplayName("마크다운 코드펜스가 붙어도 파싱한다")
    void 코드펜스를_벗겨낸다() {
        AiVerificationResult result = analyzer.parse("""
                ```json
                {"verdicts":[{"name":"떡볶이","matched":true,"confidence":0.88,"reason":""}]}
                ```""");

        assertThat(result.verdicts().getFirst().name()).isEqualTo("떡볶이");
    }

    @Test
    @DisplayName("앞뒤 설명 문장이 섞여도 JSON 본문만 잘라 파싱한다")
    void 군더더기_설명을_무시한다() {
        AiVerificationResult result = analyzer.parse("""
                사진을 확인했습니다:
                {"verdicts":[{"name":"삼겹살","matched":true,"confidence":0.95,"reason":""}]}
                도움이 되었길 바랍니다.""");

        assertThat(result.verdicts().getFirst().name()).isEqualTo("삼겹살");
    }

    @Test
    @DisplayName("verdicts 키가 없어도 예외 대신 빈 결과로 취급한다")
    void verdicts_누락은_빈_결과다() {
        assertThat(analyzer.parse("{}").verdicts()).isEmpty();
    }

    @Test
    @DisplayName("모르는 필드가 섞여도 무시한다")
    void 모르는_필드를_무시한다() {
        AiVerificationResult result = analyzer.parse("""
                {"verdicts":[{"name":"라면","matched":true,"confidence":0.6,"reason":"","note":"추가"}],"version":2}""");

        assertThat(result.verdicts().getFirst().name()).isEqualTo("라면");
    }

    @Test
    @DisplayName("JSON이 아예 없으면 파싱 실패 코드로 예외")
    void JSON이_없으면_예외() {
        assertThatThrownBy(() -> analyzer.parse("죄송합니다. 판정할 수 없습니다."))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "AI_RESPONSE_PARSE_FAILED");
    }

    @Test
    @DisplayName("깨진 JSON이면 파싱 실패 코드로 예외")
    void 깨진_JSON이면_예외() {
        assertThatThrownBy(() -> analyzer.parse("{\"verdicts\":[{\"name\":"))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "AI_RESPONSE_PARSE_FAILED");
    }

    @Test
    @DisplayName("빈 응답이면 전용 코드로 예외")
    void 빈_응답이면_예외() {
        assertThatThrownBy(() -> analyzer.parse("  "))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "AI_RESPONSE_EMPTY");
    }

    @ParameterizedTest(name = "api-key=\"{0}\"")
    @ValueSource(strings = {"", "   ", "missing-groq-api-key"})
    @DisplayName("API 키가 없으면 호출 전에 막는다 — 센티널 기본값도 미설정으로 본다")
    void 키가_없으면_호출하지_않는다(String apiKey) {
        VisionAnalyzer keyless = new VisionAnalyzer(
                mock(ChatModel.class),
                new ObjectMapper(),
                new VisionSpikeProperties(5, 1024, 0.8f, 3_500_000L, 5, true, "none"),
                apiKey
        );

        assertThatThrownBy(() -> keyless.analyze(List.of(), List.of("김밥")))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "AI_KEY_MISSING");
    }
}
