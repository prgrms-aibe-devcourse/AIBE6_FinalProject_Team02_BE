package com.backend_catcheat.domain.dex.basicdex.service;

import com.backend_catcheat.domain.dex.basicdex.dto.BasicDexResponse;
import com.backend_catcheat.domain.dex.basicdex.entity.BasicDexEntity;
import com.backend_catcheat.domain.dex.basicdex.repository.BasicDexRepository;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BasicDexService {
    private final BasicDexRepository basicDexRepository;
    private final S3PresignedUrlService s3PresignedUrlService;

    @Transactional(readOnly = true)
    public List<BasicDexResponse> findAll() {
        return basicDexRepository.findAllByOrderByIdAsc().stream()
                .map(entity -> BasicDexResponse.of(
                        entity,
                        s3PresignedUrlService.createDownloadUrl(resolveIllustrationLocation(entity))
                ))
                .toList();
    }

    private String resolveIllustrationLocation(BasicDexEntity entity) {
        if (entity.getIllustrationUrl() != null && !entity.getIllustrationUrl().isBlank()) {
            return entity.getIllustrationUrl();
        }
        String key = entity.getCategory().getIllustrationFolderName() + "/" + entity.getName() + ".png";
        return Normalizer.normalize(key, Normalizer.Form.NFD);
    }
}
