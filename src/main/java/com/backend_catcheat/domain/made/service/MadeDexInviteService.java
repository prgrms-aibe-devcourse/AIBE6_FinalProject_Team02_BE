package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.made.dto.MadeDexInvitePreviewDTO;
import com.backend_catcheat.domain.made.dto.MadeDexInviteResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexJoinResponseDTO;
import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.entity.MadeDexInvite;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.repository.MadeDexInviteRepository;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
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

    private final MadeDexFinder madeDexFinder;
    private final MadeDexMemberRepository madeDexMemberRepository;
    private final MadeDexInviteRepository madeDexInviteRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final Clock clock;

    /** 그룹장이 코드를 새로 뽑는다. 살아 있던 코드는 무효화된다 */
    @Transactional
    public MadeDexInviteResponseDTO issue(Long userId, Long madeDexId) {
        // 참여와 같은 행을 잠근다. 그래야 "코드를 죽이는 일"과 "코드로 들어오는 일"이 한 줄로 선다
        MadeDex madeDex = madeDexFinder.locked(madeDexId);
        madeDex.requireOwner(userId);

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
        madeDexFinder.active(madeDexId).requireOwner(userId);

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
        MadeDexInvite invite = resolveInvite(rawCode);
        Long madeDexId = invite.getMadeDexId();

        MadeDex locked = madeDexFinder.locked(madeDexId);

        // 잠금을 기다리는 사이 그룹장이 재발급했을 수 있다. 잠근 뒤 코드를 다시 확인한다
        if (!madeDexInviteRepository.existsUsableByCode(invite.getCode(), LocalDateTime.now(clock))) {
            throw new CustomException(ErrorCode.MADE_DEX_INVITE_CODE_EXPIRED);
        }

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

    /** 코드 → 쓸 수 있는 초대. 코드가 없거나 죽었으면 각각의 이유로 던진다 */
    private MadeDexInvite resolveInvite(String rawCode) {
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
        return invite;
    }

    /** 코드 → 살아 있는 그룹. 읽기 전용 경로(미리보기)라 잠그지 않는다 */
    private MadeDex resolveGroup(String rawCode) {
        return madeDexFinder.active(resolveInvite(rawCode).getMadeDexId());
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

    /**
     * 손으로 옮겨 적거나 메신저에서 복사한 값이라 서버에서 형태를 맞춘다.
     * 영숫자가 아닌 문자(공백·하이픈·따옴표 등)는 모두 버리고 대문자로 올린다 —
     * 남은 여섯 글자가 실제로 존재해야 하므로, 관대하게 받아도 아무나 들어오지 못한다.
     */
    private String normalize(String rawCode) {
        if (!StringUtils.hasText(rawCode)) {
            return null;
        }
        String code = rawCode.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return code.isEmpty() ? null : code;
    }
}
