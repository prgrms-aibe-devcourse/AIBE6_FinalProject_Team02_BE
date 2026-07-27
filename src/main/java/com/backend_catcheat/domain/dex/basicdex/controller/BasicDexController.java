package com.backend_catcheat.domain.dex.basicdex.controller;

import com.backend_catcheat.domain.dex.basicdex.service.BasicDexService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dex")
@RequiredArgsConstructor
public class BasicDexController {
    private final BasicDexService basicDexService;
}
