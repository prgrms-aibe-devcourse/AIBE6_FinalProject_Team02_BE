package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.repository.MadeDexRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MadeDexFinder {

    private final MadeDexRepository madeDexRepository;

    public MadeDex active(Long madeDexId) {
        return madeDexRepository.findByIdAndDeletedAtIsNull(madeDexId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_NOT_FOUND));
    }

    // 정원·그룹장 판정 전에 잠근다. 검사와 실행 사이에 끼어들면 13번째 멤버나 그룹장 둘이 생긴다
    public MadeDex locked(Long madeDexId) {
        return madeDexRepository.findActiveByIdForUpdate(madeDexId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_NOT_FOUND));
    }
}
