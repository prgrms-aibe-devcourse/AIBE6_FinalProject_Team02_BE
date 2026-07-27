package com.backend_catcheat.domain.dex.basicdex.controller;

import com.backend_catcheat.domain.dex.basicdex.dto.BasicDexResponse;
import com.backend_catcheat.domain.dex.basicdex.service.BasicDexService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dex")
@RequiredArgsConstructor
public class BasicDexController {
    private final BasicDexService basicDexService;

    @GetMapping("/basic")
    public ApiResponse<List<BasicDexResponse>> getBasicDex() {
        return ApiResponse.ok(basicDexService.findAll());
    }
}
