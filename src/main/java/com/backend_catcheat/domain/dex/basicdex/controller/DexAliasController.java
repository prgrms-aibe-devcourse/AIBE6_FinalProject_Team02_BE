package com.backend_catcheat.domain.dex.basicdex.controller;

import com.backend_catcheat.domain.dex.basicdex.service.DexAliasService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "베이짓 · 음식 별칭", description = "등록 화면의 도감 검색(초성·별칭)용 사전. 칸 목록과 분리해 별칭만 내려준다.")
@RestController
@RequestMapping("/api/v1/dex")
@RequiredArgsConstructor
public class DexAliasController {

    private final DexAliasService dexAliasService;

    /**
     * GET /api/v1/dex/aliases → { "1": ["돼지김치찌개", "김치찌게"], "4": ["냉면", "물냉"], ... }
     *
     * 등록 화면에서 도감 검색(초성·별칭)을 돌리기 위한 사전이다. 칸 목록은 /api/v1/dex/basic이
     * 이미 주므로 여기서는 별칭만 넘긴다 — 그리드는 별칭이 필요 없어 같이 실을 이유가 없다.
     *
     * 시큐리티는 기본값(anyRequest().authenticated())으로 보호된다. 등록 플로우 전용이라
     * 로그인 사용자만 쓰면 되고, 그래서 permitAll 목록에 넣지 않았다.
     */
    @Operation(summary = "음식 별칭 사전 조회", description = """
            `{ 칸ID: [별칭...] }` 형태로 전부 내려준다.
            예) `{ "1": ["돼지김치찌개", "김치찌게"], "4": ["냉면", "물냉"] }`

            칸 목록은 기본 도감 조회가 이미 주므로 여기서는 별칭만 넘긴다.
            """)
    @GetMapping("/aliases")
    public ApiResponse<Map<Long, List<String>>> getAliases() {
        return ApiResponse.ok(dexAliasService.findAll());
    }
}
