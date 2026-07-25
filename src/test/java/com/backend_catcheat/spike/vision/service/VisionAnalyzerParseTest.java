package com.backend_catcheat.spike.vision.service;

import com.backend_catcheat.spike.vision.config.VisionSpikeProperties;
import com.backend_catcheat.spike.vision.dto.ai.AiVisionResult;
import com.backend_catcheat.spike.vision.exception.VisionSpikeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("AI 응답 파싱")
class VisionAnalyzerParseTest {

    private VisionAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new VisionAnalyzer(
                mock(ChatModel.class),
                new ObjectMapper(),
                new VisionSpikeProperties(5, 1024, 0.8f, 3_500_000L, true),
                "dummy-key"
        );
    }

    @Test
    @DisplayName("단일 확신 — 후보 1개")
    void 단일_확신을_파싱한다() {
        String raw = """
                {"foods":[{"candidates":[{"name":"김치찌개","confidence":0.92}]}]}""";

        AiVisionResult result = analyzer.parse(raw);

        assertThat(result.foods()).hasSize(1);
        assertThat(result.foods().getFirst().candidates()).hasSize(1);
        assertThat(result.foods().getFirst().candidates().getFirst().name()).isEqualTo("김치찌개");
        assertThat(result.foods().getFirst().candidates().getFirst().confidence()).isEqualTo(0.92);
    }

    @Test
    @DisplayName("유사 후보 — 한 음식에 후보 3개")
    void 유사_후보를_파싱한다() {
        String raw = """
                {"foods":[{"candidates":[
                  {"name":"칼국수","confidence":0.45},
                  {"name":"잔치국수","confidence":0.35},
                  {"name":"수제비","confidence":0.20}
                ]}]}""";

        AiVisionResult result = analyzer.parse(raw);

        assertThat(result.foods()).hasSize(1);
        assertThat(result.foods().getFirst().candidates()).hasSize(3);
    }

    @Test
    @DisplayName("복수 음식 검출 — 한 상 사진")
    void 복수_음식을_파싱한다() {
        String raw = """
                {"foods":[
                  {"candidates":[{"name":"김치찌개","confidence":0.9}]},
                  {"candidates":[{"name":"계란말이","confidence":0.8}]},
                  {"candidates":[{"name":"제육볶음","confidence":0.7}]}
                ]}""";

        AiVisionResult result = analyzer.parse(raw);

        assertThat(result.foods()).hasSize(3);
    }

    @Test
    @DisplayName("마크다운 코드펜스가 붙어도 파싱한다")
    void 코드펜스를_벗겨낸다() {
        String raw = """
                ```json
                {"foods":[{"candidates":[{"name":"떡볶이","confidence":0.88}]}]}
                ```""";

        AiVisionResult result = analyzer.parse(raw);

        assertThat(result.foods().getFirst().candidates().getFirst().name()).isEqualTo("떡볶이");
    }

    @Test
    @DisplayName("앞뒤 설명 문장이 섞여도 JSON 본문만 잘라 파싱한다")
    void 군더더기_설명을_무시한다() {
        String raw = """
                사진을 분석했습니다. 결과는 다음과 같습니다:
                {"foods":[{"candidates":[{"name":"삼겹살","confidence":0.95}]}]}
                도움이 되었길 바랍니다.""";

        AiVisionResult result = analyzer.parse(raw);

        assertThat(result.foods().getFirst().candidates().getFirst().name()).isEqualTo("삼겹살");
    }

    @Test
    @DisplayName("음식을 못 찾으면 빈 배열 — 재분석 플로우로 넘어간다")
    void 빈_결과를_파싱한다() {
        AiVisionResult result = analyzer.parse("""
                {"foods":[]}""");

        assertThat(result.foods()).isEmpty();
    }

    @Test
    @DisplayName("foods 키가 없어도 예외 대신 빈 결과로 취급한다")
    void foods_누락은_빈_결과다() {
        AiVisionResult result = analyzer.parse("{}");

        assertThat(result.foods()).isEmpty();
    }

    @Test
    @DisplayName("모르는 필드가 섞여도 무시한다")
    void 모르는_필드를_무시한다() {
        String raw = """
                {"foods":[{"note":"확실하지 않음","candidates":[{"name":"라면","confidence":0.6,"source":"vision"}]}],"version":2}""";

        AiVisionResult result = analyzer.parse(raw);

        assertThat(result.foods().getFirst().candidates().getFirst().name()).isEqualTo("라면");
    }

    @Test
    @DisplayName("JSON이 아예 없으면 파싱 실패 코드로 예외")
    void JSON이_없으면_예외() {
        assertThatThrownBy(() -> analyzer.parse("죄송합니다. 음식을 판별할 수 없습니다."))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "AI_RESPONSE_PARSE_FAILED");
    }

    @Test
    @DisplayName("깨진 JSON이면 파싱 실패 코드로 예외")
    void 깨진_JSON이면_예외() {
        assertThatThrownBy(() -> analyzer.parse("{\"foods\":[{\"candidates\":[{\"name\":"))
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
                new VisionSpikeProperties(5, 1024, 0.8f, 3_500_000L, true),
                apiKey
        );

        assertThatThrownBy(() -> keyless.analyze(java.util.List.of(), null))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "AI_KEY_MISSING");
    }
}
