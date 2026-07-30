package com.backend_catcheat.domain.place.service;

import com.backend_catcheat.domain.place.config.PlaceProperties;
import com.backend_catcheat.domain.place.dto.KakaoKeywordResponse;
import com.backend_catcheat.domain.place.dto.KakaoKeywordResponse.Document;
import com.backend_catcheat.domain.place.dto.PlaceSummary;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private static final String KEYWORD_SEARCH_PATH = "/v2/local/search/keyword.json";

    // 요청 파라미터 category_group_code는 코드를 하나만 받으므로(둘을 주면 400) 받아서 여기서 거른다
    private static final Set<String> FOOD_CATEGORY_GROUPS = Set.of("FD6", "CE7");

    /** 카카오 상한은 20,000m */
    private static final int NEARBY_RADIUS_M = 20_000;

    // 좌표만 주면 반경만 잘리고 순서는 정확도순으로 남는다. 가까운 순은 이 값을 줘야 한다
    private static final String SORT_BY_DISTANCE = "distance";

    private final RestClient kakaoLocalRestClient;
    private final PlaceProperties properties;

    /**
     * @param lat 선택. 좌표가 오면 반경 20km 안을 가까운 순으로, 없으면 전국 정확도순
     */
    public List<PlaceSummary> search(String query, Double lat, Double lng) {
        String keyword = query == null ? "" : query.trim();

        // 한 글자로는 쓸 만한 후보가 안 나온다. 타이핑 중 왕복을 아낀다
        if (keyword.length() < properties.minQueryLength()) {
            return List.of();
        }

        KakaoKeywordResponse response;
        try {
            response = kakaoLocalRestClient.get()
                    .uri(builder -> {
                        builder.path(KEYWORD_SEARCH_PATH)
                                .queryParam("query", keyword)
                                .queryParam("size", properties.size());
                        if (lat != null && lng != null) {
                            builder.queryParam("x", lng)
                                    .queryParam("y", lat)
                                    .queryParam("radius", NEARBY_RADIUS_M)
                                    .queryParam("sort", SORT_BY_DISTANCE);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(KakaoKeywordResponse.class);
        } catch (RestClientException e) {
            log.error("[장소] 카카오 로컬 검색 실패. query={}", keyword, e);
            throw new CustomException(ErrorCode.PLACE_SEARCH_FAILED);
        }

        if (response == null || response.documents() == null) {
            return List.of();
        }
        return response.documents().stream()
                // Set.of(...).contains(null)이 NPE라서 먼저 막는다
                .filter(document -> document.categoryGroupCode() != null)
                .filter(document -> FOOD_CATEGORY_GROUPS.contains(document.categoryGroupCode()))
                .map(PlaceSearchService::toSummary)
                .toList();
    }

    private static PlaceSummary toSummary(Document document) {
        return new PlaceSummary(
                document.id(),
                document.placeName(),
                lastCategory(document.categoryName()),
                emptyToNull(document.roadAddressName()),
                emptyToNull(document.addressName()),
                // 카카오는 x가 경도, y가 위도다. 뒤집으면 엉뚱한 곳에 찍힌다
                parseCoordinate(document.y()),
                parseCoordinate(document.x()));
    }

    private static String lastCategory(String categoryName) {
        if (!StringUtils.hasText(categoryName)) {
            return null;
        }
        String[] parts = categoryName.split(">");
        return parts[parts.length - 1].trim();
    }

    private static Double parseCoordinate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Double.valueOf(raw);
        } catch (NumberFormatException e) {
            // 좌표 하나 때문에 검색 전체를 실패시키지 않는다
            log.warn("[장소] 좌표를 읽지 못했습니다: {}", raw);
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
