package com.backend_catcheat.domain.upload.service;

import com.backend_catcheat.domain.upload.dto.UploadPurpose;
import com.backend_catcheat.domain.upload.entity.UploadObject;
import com.backend_catcheat.domain.upload.repository.UploadObjectRepository;
import com.backend_catcheat.global.config.TimeConfig;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 발급받은 사람과 용도를 남겨, 남의 key를 자기 기록에 붙이지 못하게 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UploadObjectService {

    private final UploadObjectRepository uploadObjectRepository;
    private final Clock clock;

    @Transactional
    public void issued(Long uploaderId, List<String> imageKeys, UploadPurpose purpose) {
        // 인증 없이 발급되는 경로가 남아 있으면 주인을 적을 수 없다. 그때는 기록하지 않고 통과시킨다
        if (uploaderId == null || imageKeys.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(TimeConfig.SERVICE_ZONE));
        uploadObjectRepository.saveAll(imageKeys.stream()
                .map(key -> UploadObject.issued(key, uploaderId, purpose, now))
                .toList());
    }

    /**
     * 이 key들을 쓸 자격이 있는지 본다.
     * 표에 없는 key는 이 기능 이전에 발급된 것이라 막지 않는다 — 막으면 기존 기록 수정이 끊긴다.
     */
    public void requireUsableBy(Long userId, Collection<String> imageKeys, UploadPurpose purpose) {
        if (imageKeys.isEmpty()) {
            return;
        }
        Map<String, UploadObject> known = uploadObjectRepository.findByImageKeyIn(imageKeys).stream()
                .collect(Collectors.toMap(UploadObject::getImageKey, Function.identity()));

        for (String imageKey : imageKeys) {
            UploadObject object = known.get(imageKey);
            if (object == null) {
                continue;
            }
            if (!object.uploadedBy(userId)) {
                throw new CustomException(ErrorCode.UPLOAD_OBJECT_NOT_OWNED);
            }
            if (!object.isFor(purpose)) {
                throw new CustomException(ErrorCode.UPLOAD_OBJECT_PURPOSE_MISMATCH);
            }
        }
    }

    @Transactional
    public void forget(String imageKey) {
        uploadObjectRepository.deleteByImageKey(imageKey);
    }
}
