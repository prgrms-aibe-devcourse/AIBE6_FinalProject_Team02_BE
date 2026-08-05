package com.backend_catcheat.domain.place.service;

import com.backend_catcheat.domain.place.config.PlaceProperties;
import com.backend_catcheat.domain.place.dto.GeoPoint;
import com.backend_catcheat.domain.place.dto.KakaoAddressResponse;
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
    private static final String ADDRESS_SEARCH_PATH = "/v2/local/search/address.json";
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

    /** 주소/상호 문자열 → 좌표. 주소검색 우선, 실패하면 키워드검색으로 폴백 */
    public GeoPoint geocode(String address) {
        String query = address == null ? "" : address.trim();
        if (query.isEmpty()) {
            throw new CustomException(ErrorCode.PLACE_ADDRESS_NOT_FOUND);
        }
        GeoPoint byAddress = tryAddressSearch(query);
        if (byAddress != null) {
            return byAddress;
        }
        GeoPoint byKeyword = tryKeywordCoordinate(query); // 부분 주소·상호명 대응
        if (byKeyword != null) {
            return byKeyword;
        }
        throw new CustomException(ErrorCode.PLACE_ADDRESS_NOT_FOUND);
    }

    private GeoPoint tryAddressSearch(String query) {
        KakaoAddressResponse response;
        try {
            response = kakaoLocalRestClient.get()
                    .uri(uri -> uri.path(ADDRESS_SEARCH_PATH)
                            .queryParam("query", query)
                            .queryParam("size", 1)
                            .build())
                    .retrieve()
                    .body(KakaoAddressResponse.class);
        } catch (RestClientException e) {
            log.warn("[장소] 주소 검색 실패. query={}", query, e);
            return null;
        }
        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            return null;
        }
        KakaoAddressResponse.Document d = response.documents().get(0);
        if (d.x() == null || d.y() == null) {
            return null;
        }
        // 카카오는 x가 경도, y가 위도
        return new GeoPoint(d.addressName(), parseCoordinate(d.y()), parseCoordinate(d.x()));
    }

    private GeoPoint tryKeywordCoordinate(String query) {
        KakaoKeywordResponse response;
        try {
            response = kakaoLocalRestClient.get()
                    .uri(uri -> uri.path(KEYWORD_SEARCH_PATH)
                            .queryParam("query", query)
                            .queryParam("size", 1)
                            .build())
                    .retrieve()
                    .body(KakaoKeywordResponse.class);
        } catch (RestClientException e) {
            log.warn("[장소] 키워드 좌표 검색 실패. query={}", query, e);
            return null;
        }
        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            return null;
        }
        KakaoKeywordResponse.Document d = response.documents().get(0);
        if (d.x() == null || d.y() == null) {
            return null;
        }
        String label = d.roadAddressName() != null && !d.roadAddressName().isBlank()
                ? d.roadAddressName()
                : (d.addressName() != null ? d.addressName() : d.placeName());
        return new GeoPoint(label, parseCoordinate(d.y()), parseCoordinate(d.x()));
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
