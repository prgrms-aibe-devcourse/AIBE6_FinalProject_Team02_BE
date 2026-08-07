package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.made.dto.MadeDexCreateRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexCreateResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDetailDTO;
import com.backend_catcheat.domain.made.dto.MadeDexMemberCountDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSummaryDTO;
import com.backend_catcheat.domain.made.dto.MadeDexUpdateRequestDTO;
import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.entity.MadeDexRole;
import com.backend_catcheat.domain.made.entity.MadeDexSlot;
import com.backend_catcheat.domain.made.entity.Visibility;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRepository;
import com.backend_catcheat.domain.made.repository.MadeDexSlotRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.event.MadeDexCreatedEvent;
import com.backend_catcheat.global.event.S3ObjectUnusedEvent;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MadeDexService {

    private final MadeDexRepository madeDexRepository;
    private final MadeDexMemberRepository madeDexMemberRepository;
    private final MadeDexSlotRepository madeDexSlotRepository;
    private final MadeDexFinder madeDexFinder;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public MadeDexCreateResponseDTO create(Long ownerId, MadeDexCreateRequestDTO request) {
        MadeDex madeDex = madeDexRepository.save(MadeDex.open(
                ownerId,
                requireName(request.name()),
                validDescription(request.description()),
                alwaysPrivate(),
                validImageKey(request.imageKey())));
        madeDexMemberRepository.save(
                MadeDexMember.owner(madeDex.getId(), ownerId, LocalDateTime.now(clock)));

        // 슬롯이 하나도 없으면 첫 기록을 남길 곳이 없다. 개설과 같은 트랜잭션에서 만든다
        madeDexSlotRepository.saveAll(MadeDexSlot.defaultsFor(madeDex.getId()));

        // 첫 제작 도감 뱃지 지급 트리거
        eventPublisher.publishEvent(new MadeDexCreatedEvent(ownerId));

        return new MadeDexCreateResponseDTO(madeDex.getId());
    }

    /** 공개 도감은 참여하지 않아도 열람할 수 있다. 비공개는 멤버만 */
    public MadeDexDetailDTO findDetail(Long userId, Long madeDexId) {
        MadeDexFinder.MadeDexAccess access = madeDexFinder.readable(userId, madeDexId);
        MadeDex madeDex = access.madeDex();
        MadeDexRole myRole = access.myRole();

        return new MadeDexDetailDTO(
                madeDex.getId(),
                madeDex.getName(),
                madeDex.getDescription(),
                madeDex.getVisibility(),
                s3PresignedUrlService.createDownloadUrl(madeDex.getImageKey()),
                madeDex.getImageKey(),
                madeDexMemberRepository.countByMadeDexId(madeDexId),
                madeDex.getMaxMembers(),
                myRole,
                madeDex.getCreatedAt());
    }

    @Transactional
    public void update(Long userId, Long madeDexId, MadeDexUpdateRequestDTO request) {
        // 위임과 같은 행을 잠근다. 그래야 방금 그룹장을 넘긴 사람이 수정까지 마치지 못한다
        MadeDex madeDex = madeDexFinder.locked(madeDexId);
        madeDex.requireOwner(userId);

        String imageKey = validImageKey(request.imageKey());
        String oldImageKey = madeDex.getImageKey();

        madeDex.update(
                requireName(request.name()),
                validDescription(request.description()),
                alwaysPrivate(),
                imageKey);

        // 표지를 바꾸거나 비우면 이전 객체는 아무도 참조하지 않는다.
        // 삭제는 커밋 이후로 미룬다 — 여기서 지우면 롤백됐을 때 되살릴 수 없다
        if (oldImageKey != null && !oldImageKey.equals(imageKey)) {
            eventPublisher.publishEvent(new S3ObjectUnusedEvent(oldImageKey));
        }
    }

    public List<MadeDexSummaryDTO> findMine(Long userId) {
        Map<Long, MadeDexRole> roleByMadeDexId = madeDexMemberRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(MadeDexMember::getMadeDexId, MadeDexMember::getRole));
        if (roleByMadeDexId.isEmpty()) {
            return List.of();
        }

        Set<Long> madeDexIds = roleByMadeDexId.keySet();
        Map<Long, Long> memberCountByMadeDexId =
                madeDexMemberRepository.countByMadeDexIds(madeDexIds).stream()
                        .collect(Collectors.toMap(
                                MadeDexMemberCountDTO::madeDexId, MadeDexMemberCountDTO::memberCount));

        return madeDexRepository.findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(madeDexIds).stream()
                .map(madeDex -> new MadeDexSummaryDTO(
                        madeDex.getId(),
                        madeDex.getName(),
                        madeDex.getDescription(),
                        madeDex.getVisibility(),
                        s3PresignedUrlService.createDownloadUrl(madeDex.getImageKey()),
                        memberCountByMadeDexId.getOrDefault(madeDex.getId(), 0L),
                        roleByMadeDexId.get(madeDex.getId())))
                .toList();
    }

    private String requireName(String rawName) {
        String name = blankToNull(rawName);
        if (name == null) {
            throw new CustomException(ErrorCode.MADE_DEX_NAME_REQUIRED);
        }
        if (name.length() > MadeDex.NAME_MAX) {
            throw new CustomException(ErrorCode.MADE_DEX_NAME_TOO_LONG);
        }
        return name;
    }

    private String validDescription(String rawDescription) {
        String description = blankToNull(rawDescription);
        if (description != null && description.length() > MadeDex.DESCRIPTION_MAX) {
            throw new CustomException(ErrorCode.MADE_DEX_DESCRIPTION_TOO_LONG);
        }
        return description;
    }

    private String validImageKey(String rawImageKey) {
        String imageKey = blankToNull(rawImageKey);
        if (imageKey != null && imageKey.length() > MadeDex.IMAGE_KEY_MAX) {
            throw new CustomException(ErrorCode.MADE_DEX_IMAGE_KEY_TOO_LONG);
        }
        return imageKey;
    }

    /**
     * 로그잇에는 공개 개념이 없다. 요청에 PUBLIC이 와도 비공개로 만든다.
     * 거절하지 않는 이유는 아직 공개 선택이 남아 있는 화면을 깨뜨리지 않기 위해서다.
     */
    private Visibility alwaysPrivate() {
        return Visibility.PRIVATE;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
