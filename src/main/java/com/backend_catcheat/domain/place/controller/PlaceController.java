package com.backend_catcheat.domain.place.controller;

import com.backend_catcheat.domain.place.dto.PlaceSummary;
import com.backend_catcheat.domain.place.service.PlaceSearchService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceSearchService placeSearchService;

    @GetMapping
    public ApiResponse<List<PlaceSummary>> search(
            @RequestParam String query,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return ApiResponse.ok(placeSearchService.search(query, lat, lng));
    }
}
