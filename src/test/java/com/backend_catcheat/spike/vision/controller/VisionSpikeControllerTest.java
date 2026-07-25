package com.backend_catcheat.spike.vision.controller;

import com.backend_catcheat.spike.vision.config.SpikeSecurityConfig;
import com.backend_catcheat.spike.vision.dto.SpikeMetrics;
import com.backend_catcheat.spike.vision.dto.VisionAnalysisResponse;
import com.backend_catcheat.spike.vision.dto.VisionAnalysisResponse.DetectedFood;
import com.backend_catcheat.spike.vision.dto.VisionAnalysisResponse.FoodCandidate;
import com.backend_catcheat.spike.vision.exception.VisionSpikeException;
import com.backend_catcheat.spike.vision.service.VisionSpikeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VisionSpikeController.class)
@Import(SpikeSecurityConfig.class)
@ActiveProfiles("dev")
@DisplayName("비전 스파이크 엔드포인트")
class VisionSpikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VisionSpikeService visionSpikeService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final String ENDPOINT = "/api/v1/spike/vision";

    @Test
    @DisplayName("성공 응답은 success/data 포맷을 따른다")
    void 성공_응답_포맷() throws Exception {
        when(visionSpikeService.analyze(anyList(), any())).thenReturn(new VisionAnalysisResponse(
                List.of(new DetectedFood(List.of(
                        new FoodCandidate("김치찌개", 0.93, 1L, "김치찌개", "찌개·전골", "EXACT")))),
                new SpikeMetrics(120, 1800, 3, 1, 1930, 1, 250_000, 90_000, 1200, 100, 1300, "llama-4-scout"),
                "{\"foods\":[]}"
        ));

        mockMvc.perform(multipart(ENDPOINT).file(jpegPart()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.foods[0].candidates[0].slotName").value("김치찌개"))
                .andExpect(jsonPath("$.data.foods[0].candidates[0].matchType").value("EXACT"))
                .andExpect(jsonPath("$.data.metrics.totalMs").value(1930))
                // 공용 래퍼는 null 필드를 생략하지 않으므로 "부재"가 아니라 "null"을 단언한다
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("실패 응답은 success/error 포맷을 따르고 code를 포함한다")
    void 실패_응답_포맷() throws Exception {
        when(visionSpikeService.analyze(anyList(), any()))
                .thenThrow(new VisionSpikeException("IMAGE_COUNT_EXCEEDED", "사진은 최대 5장까지 올릴 수 있어요"));

        mockMvc.perform(multipart(ENDPOINT).file(jpegPart()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("IMAGE_COUNT_EXCEEDED"))
                .andExpect(jsonPath("$.error.message").value("사진은 최대 5장까지 올릴 수 있어요"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("images 파트가 아예 없어도 공통 포맷으로 응답한다 — 스택트레이스 노출 방지")
    void 파트_누락도_공통_포맷이다() throws Exception {
        when(visionSpikeService.analyze(any(), any()))
                .thenThrow(new VisionSpikeException("IMAGE_REQUIRED", "사진을 최소 1장 올려 주세요"));

        mockMvc.perform(multipart(ENDPOINT).param("hint", "김치찌개"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("IMAGE_REQUIRED"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("AI 장애는 500으로 내보낸다 — 입력 문제와 구분")
    void AI_장애는_500이다() throws Exception {
        when(visionSpikeService.analyze(anyList(), any()))
                .thenThrow(new VisionSpikeException("AI_CALL_FAILED", "음식 분석에 실패했어요. 잠시 후 다시 시도해 주세요"));

        mockMvc.perform(multipart(ENDPOINT).file(jpegPart()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("AI_CALL_FAILED"));
    }

    @Test
    @DisplayName("인증 없이 호출할 수 있다 — 스파이크 경로는 dev에서 열려 있다")
    void 인증_없이_호출된다() throws Exception {
        when(visionSpikeService.analyze(anyList(), any())).thenReturn(new VisionAnalysisResponse(
                List.of(),
                new SpikeMetrics(1, 1, 1, 1, 4, 1, 1, 1, 0, 0, 0, "llama-4-scout"),
                "{}"
        ));

        mockMvc.perform(multipart(ENDPOINT).file(jpegPart()))
                .andExpect(status().isOk());
    }

    private static MockMultipartFile jpegPart() {
        return new MockMultipartFile("images", "food.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }
}
