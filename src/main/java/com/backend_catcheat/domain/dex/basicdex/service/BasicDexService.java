package com.backend_catcheat.domain.dex.basicdex.service;

import com.backend_catcheat.domain.dex.basicdex.repository.BasicDexRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BasicDexService {
    private final BasicDexRepository basicDexRepository;
}
