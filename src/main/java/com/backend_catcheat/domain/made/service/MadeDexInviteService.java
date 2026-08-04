package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.made.dto.MadeDexInvitePreviewDTO;
import com.backend_catcheat.domain.made.dto.MadeDexInviteResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexJoinResponseDTO;
import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.entity.MadeDexInvite;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.repository.MadeDexInviteRepository;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 초대 코드 발급과 코드로 참여하기.
 *
 * 규칙 세 가지:
 *  - 발급은 그룹장만. 재발급하면 이전 코드는 그 자리에서 죽는다(그룹당 유효 코드 1개)
 *  - 코드는 발급 후 7일간 유효하고, 그동안 여러 명이 같은 코드로 들어올 수 있다
 *  - 참여는 그룹 행을 잠근 뒤 정원을 세고 넣는다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MadeDexInviteService {

    /** 코드 충돌 시 재시도 횟수. 31^6 공간이라 1회로도 충분하지만 여유를 둔다 */
    private static final int CODE_ATTEMPTS = 5;

    private final MadeDexRepository madeDexRepository;
    private final MadeDexMemberRepository madeDexMemberRepository;
    private final MadeDexInviteRepository madeDexInviteRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final Clock clock;

    /** 그룹장이 코드를 새로 뽑는다. 살아 있던 코드는 무효화된다 */
    @Transactional
    public MadeDexInviteResponseDTO issue(Long userId, Long madeDexId) {
        MadeDex madeDex = madeDexRepository.findByIdAndDeletedAtIsNull(madeDexId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_NOT_FOUND));
        requireOwner(madeDex, userId);

        LocalDateTime now = LocalDateTime.now(clock);
        madeDexInviteRepository.revokeActive(madeDexId, now);

        MadeDexInvite invite = madeDexInviteRepository.save(
                MadeDexInvite.issue(madeDexId, nextCode(), userId, now));

        return new MadeDexInviteResponseDTO(invite.getCode(), invite.getExpiresAt());
    }

    /**
     * 참여자 관리 화면 진입 시 현재 코드. 유효한 코드가 없으면 null —
     * 아직 한 번도 안 뽑았거나 만료된 상태이고, 화면은 "코드 만들기"를 보여준다.
     */
    public MadeDexInviteResponseDTO findActive(Long userId, Long madeDexId) {
        MadeDex madeDex = madeDexRepository.findByIdAndDeletedAtIsNull(madeDexId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_NOT_FOUND));
        requireOwner(madeDex, userId);

        return madeDexInviteRepository
                .findFirstByMadeDexIdAndRevokedAtIsNullAndExpiresAtAfterOrderByIdDesc(
                        madeDexId, LocalDateTime.now(clock))
                .map(invite -> new MadeDexInviteResponseDTO(invite.getCode(), invite.getExpiresAt()))
                .orElse(null);
    }

    /** 코드/링크로 들어온 사람에게 참여 전에 보여줄 그룹 정보 */
    public MadeDexInvitePreviewDTO preview(Long userId, String rawCode) {
        MadeDex madeDex = resolveGroup(rawCode);

        return new MadeDexInvitePreviewDTO(
                madeDex.getId(),
                madeDex.getName(),
                madeDex.getDescription(),
                madeDexMemberRepository.countByMadeDexId(madeDex.getId()),
                madeDex.getMaxMembers(),
                madeDexMemberRepository.existsByMadeDexIdAndUserId(madeDex.getId(), userId));
    }

    @Transactional
    public MadeDexJoinResponseDTO join(Long userId, String rawCode) {
        Long madeDexId = resolveGroup(rawCode).getId();

        // 정원 검사와 삽입 사이에 다른 참여가 끼어들지 못하도록 그룹 행을 잠근다
        MadeDex locked = madeDexRepository.findActiveByIdForUpdate(madeDexId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_NOT_FOUND));

        if (madeDexMemberRepository.existsByMadeDexIdAndUserId(madeDexId, userId)) {
            throw new CustomException(ErrorCode.MADE_DEX_ALREADY_JOINED);
        }
        if (madeDexMemberRepository.countByMadeDexId(madeDexId) >= locked.getMaxMembers()) {
            throw new CustomException(ErrorCode.MADE_DEX_FULL);
        }

        try {
            madeDexMemberRepository.save(
                    MadeDexMember.member(madeDexId, userId, LocalDateTime.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // uk_made_dex_member — 같은 사람이 두 번 눌렀을 때의 마지막 방어선
            throw new CustomException(ErrorCode.MADE_DEX_ALREADY_JOINED);
        }

        return new MadeDexJoinResponseDTO(madeDexId);
    }

    /** 코드 → 살아 있는 그룹. 코드가 없거나 죽었거나 그룹이 지워졌으면 각각의 이유로 던진다 */
    private MadeDex resolveGroup(String rawCode) {
        String code = normalize(rawCode);
        if (code == null) {
            throw new CustomException(ErrorCode.MADE_DEX_INVITE_CODE_REQUIRED);
        }

        MadeDexInvite invite = madeDexInviteRepository.findByCode(code)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_INVITE_CODE_INVALID));
        // 무효화된 코드도 사용자 입장에선 "새 코드를 받아야 한다"로 같다
        if (!invite.isUsableAt(LocalDateTime.now(clock))) {
            throw new CustomException(ErrorCode.MADE_DEX_INVITE_CODE_EXPIRED);
        }

        return madeDexRepository.findByIdAndDeletedAtIsNull(invite.getMadeDexId())
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_NOT_FOUND));
    }

    private void requireOwner(MadeDex madeDex, Long userId) {
        if (!madeDex.getOwnerId().equals(userId)) {
            throw new CustomException(ErrorCode.MADE_DEX_NOT_OWNER);
        }
    }

    private String nextCode() {
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String code = inviteCodeGenerator.generate();
            if (!madeDexInviteRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new CustomException(ErrorCode.INTERNAL_ERROR);
    }

    /** 손으로 옮겨 적은 값이라 공백·소문자·하이픈을 허용하고 서버에서 맞춘다 */
    private String normalize(String rawCode) {
        if (!StringUtils.hasText(rawCode)) {
            return null;
        }
        String code = rawCode.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return code.isEmpty() ? null : code;
    }
}
