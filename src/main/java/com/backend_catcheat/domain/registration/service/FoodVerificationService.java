package com.backend_catcheat.domain.registration.service;

import com.backend_catcheat.domain.dex.basicdex.entity.BasicDexEntity;
import com.backend_catcheat.domain.dex.basicdex.repository.BasicDexRepository;
import com.backend_catcheat.domain.registration.config.VisionProperties;
import com.backend_catcheat.domain.registration.dto.VerificationRequest;
import com.backend_catcheat.domain.registration.dto.VerificationResponse;
import com.backend_catcheat.domain.registration.dto.VerificationResponse.SlotVerdict;
import com.backend_catcheat.domain.registration.dto.ai.AiVerificationResult;
import com.backend_catcheat.domain.registration.dto.ai.AiVerificationResult.AiVerdict;
import com.backend_catcheat.domain.registration.entity.Registration;
import com.backend_catcheat.domain.registration.entity.VerificationAttempt;
import com.backend_catcheat.domain.registration.repository.RegistrationRepository;
import com.backend_catcheat.domain.registration.repository.VerificationAttemptRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodVerificationService {

    private final RegistrationRepository registrationRepository;
    private final VerificationAttemptRepository attemptRepository;
    private final BasicDexRepository slotRepository;
    private final RegistrationPhotoLoader photoLoader;
    private final ImagePreprocessor preprocessor;
    private final VisionAnalyzer analyzer;
    private final VisionProperties properties;

    @Transactional
    public VerificationResponse verify(Long userId, VerificationRequest request) {
        // 순수 입력 검증 → DB 조회 → 상태 변경 순. 잘못된 요청이 재시도 횟수를 깎으면 안 된다.
        List<String> photoKeys = validatePhotoKeys(request.photoKeys());
        String analysisPhotoKey = photoKeys.get(resolveAnalysisIndex(photoKeys, request.analysisPhotoIndex()));
        List<BasicDexEntity> slots = loadSlots(request.slotIds());

        Registration registration = openOrRetry(userId, request.registrationId(), analysisPhotoKey);

        long startedAt = System.nanoTime();
        byte[] original = photoLoader.loadForAnalysis(analysisPhotoKey);
        PreparedImage prepared = preprocessor.prepare(original, analysisPhotoKey);

        List<String> names = slots.stream().map(BasicDexEntity::getName).toList();
        AiVerificationResult aiResult = analyzer.verify(prepared, names);

        List<SlotVerdict> verdicts = toVerdicts(slots, aiResult);
        recordAttempts(registration, verdicts);

        boolean allMatched = verdicts.stream().allMatch(SlotVerdict::matched);
        long totalMs = (System.nanoTime() - startedAt) / 1_000_000;

        log.info("[등록] 검증 완료 registrationId={} {}회차 요청{}건 통과{}건 남은재시도{} {}ms",
                registration.getId(), registration.currentAttemptNo(), verdicts.size(),
                verdicts.stream().filter(SlotVerdict::matched).count(), registration.retriesLeft(), totalMs);

        if (totalMs > 5000) {
            log.warn("[등록] 목표 응답시간 5초 초과: {}ms", totalMs);
        }

        return new VerificationResponse(
                registration.getId(), verdicts, allMatched, registration.retriesLeft());
    }

    private Registration openOrRetry(Long userId, Long registrationId, String analysisPhotoKey) {
        if (registrationId == null) {
            return registrationRepository.save(Registration.start(userId, analysisPhotoKey));
        }

        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new CustomException(ErrorCode.REGISTRATION_NOT_FOUND));

        if (!registration.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.REGISTRATION_FORBIDDEN);
        }
        if (!registration.canRetry()) {
            throw new CustomException(ErrorCode.RETRY_LIMIT_EXCEEDED);
        }

        registration.retryWith(analysisPhotoKey);
        return registration;
    }

    private List<String> validatePhotoKeys(List<String> photoKeys) {
        if (photoKeys == null || photoKeys.isEmpty()) {
            throw new CustomException(ErrorCode.PHOTO_REQUIRED);
        }
        List<String> keys = photoKeys.stream().filter(StringUtils::hasText).map(String::trim).toList();
        if (keys.isEmpty()) {
            throw new CustomException(ErrorCode.PHOTO_REQUIRED);
        }
        if (keys.size() > properties.maxImages()) {
            throw new CustomException(ErrorCode.PHOTO_COUNT_EXCEEDED);
        }
        return keys;
    }

    // 요청 순서를 유지한다. 화면의 칩 순서와 어긋나면 어느 게 실패했는지 읽을 수 없다
    private List<BasicDexEntity> loadSlots(List<Long> slotIds) {
        if (slotIds == null || slotIds.isEmpty()) {
            throw new CustomException(ErrorCode.FOOD_NAME_REQUIRED);
        }
        List<Long> distinct = slotIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            throw new CustomException(ErrorCode.FOOD_NAME_REQUIRED);
        }
        if (distinct.size() > properties.maxFoodNames()) {
            throw new CustomException(ErrorCode.FOOD_NAME_COUNT_EXCEEDED);
        }

        Map<Long, BasicDexEntity> found = slotRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(BasicDexEntity::getId, Function.identity()));

        return distinct.stream()
                .map(id -> {
                    BasicDexEntity slot = found.get(id);
                    if (slot == null) {
                        throw new CustomException(ErrorCode.DEX_SLOT_NOT_FOUND);
                    }
                    return slot;
                })
                .toList();
    }

    private int resolveAnalysisIndex(List<String> photoKeys, Integer requested) {
        if (requested == null) {
            return 0;
        }
        if (requested < 0 || requested >= photoKeys.size()) {
            throw new CustomException(ErrorCode.ANALYSIS_PHOTO_INVALID);
        }
        return requested;
    }

    private List<SlotVerdict> toVerdicts(List<BasicDexEntity> slots, AiVerificationResult aiResult) {
        Map<String, AiVerdict> byName = new LinkedHashMap<>();
        for (AiVerdict verdict : aiResult.verdicts()) {
            if (verdict != null && StringUtils.hasText(verdict.name())) {
                byName.putIfAbsent(normalize(verdict.name()), verdict);
            }
        }

        return slots.stream().map(slot -> {
            AiVerdict verdict = byName.get(normalize(slot.getName()));
            // AI가 그 이름을 아예 빠뜨렸으면 통과시키지 않는다
            boolean matched = verdict != null && verdict.matched();
            String reason = matched ? "" : reasonOf(verdict);

            return new SlotVerdict(
                    slot.getId(),
                    slot.getName(),
                    slot.getCategory().getDisplayName(),
                    matched,
                    verdict != null ? verdict.confidence() : 0.0,
                    reason);
        }).toList();
    }

    private static String reasonOf(AiVerdict verdict) {
        if (verdict == null) {
            return "사진에서 이 음식을 확인하지 못했어요";
        }
        return StringUtils.hasText(verdict.reason()) ? verdict.reason() : "사진과 음식이 달라 보여요";
    }

    private void recordAttempts(Registration registration, List<SlotVerdict> verdicts) {
        int attemptNo = registration.currentAttemptNo();
        attemptRepository.saveAll(verdicts.stream()
                .map(v -> VerificationAttempt.of(
                        registration.getId(), attemptNo, v.slotId(), v.matched(), v.confidence(), v.reason()))
                .toList());
    }

    private static String normalize(String value) {
        return value.replaceAll("[\\s·\\-_]", "").toLowerCase();
    }
}
