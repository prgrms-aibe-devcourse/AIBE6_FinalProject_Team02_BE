package com.backend_catcheat.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoAddressResponse(List<Document> documents) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(String addressName, String x, String y) {
    }
}
