package com.backend_catcheat.domain.place.dto;

public record GeoPoint(
        String address,
        Double lat,
        Double lng
) {}
