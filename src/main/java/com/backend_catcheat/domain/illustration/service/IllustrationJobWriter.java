package com.backend_catcheat.domain.illustration.service;

import com.backend_catcheat.domain.illustration.entity.IllustrationStatus;
import com.backend_catcheat.domain.illustration.repository.IllustrationJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 20초짜리 외부 호출을 트랜잭션 밖에 두려고 결과 반영만 분리했다.
// IllustrationGenerator 안에 두면 자기 호출이라 프록시를 타지 않는다
@Service
@RequiredArgsConstructor
public class IllustrationJobWriter {

    private final IllustrationJobRepository illustrationJobRepository;

    @Transactional
    public void succeed(Long jobId, String resultImageKey) {
        illustrationJobRepository.findById(jobId).ifPresent(job -> job.succeed(resultImageKey));
    }

    @Transactional
    public void fail(Long jobId, IllustrationStatus status, String failureCode) {
        illustrationJobRepository.findById(jobId).ifPresent(job -> job.fail(status, failureCode));
    }
}
