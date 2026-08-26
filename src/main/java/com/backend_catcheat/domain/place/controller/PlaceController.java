package com.backend_catcheat.domain.place.controller;

import com.backend_catcheat.domain.place.dto.GeoPoint;
import com.backend_catcheat.domain.place.dto.PlaceSummary;
import com.backend_catcheat.domain.place.service.PlaceSearchService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "장소 검색", description = """
        카카오 로컬 API 를 서버가 대신 호출한다. 챌린지 슬롯에 붙일 가게를 고르고 좌표를 얻는 데 쓴다.
        여기서 얻은 좌표가 나중에 해금 인증의 기준점이 된다(허용 반경 80m).
        """)
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceSearchService placeSearchService;

    @Operation(summary = "장소 검색", description = "가게 이름으로 찾는다. `lat`·`lng` 를 주면 그 지점에 가까운 순으로 정렬된다.")
    @GetMapping
    public ApiResponse<List<PlaceSummary>> search(
            @RequestParam String query,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return ApiResponse.ok(placeSearchService.search(query, lat, lng));
    }

    @Operation(summary = "주소 → 좌표 변환", description = "주소 문자열을 위경도로 바꾼다.")
    @GetMapping("/geocode")
    public ApiResponse<GeoPoint> geocode(@RequestParam String address) {
        return ApiResponse.ok(placeSearchService.geocode(address));
    }
}
