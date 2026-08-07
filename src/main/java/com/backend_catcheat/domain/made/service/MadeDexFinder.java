package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.entity.MadeDexRole;
import com.backend_catcheat.domain.made.entity.Visibility;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MadeDexFinder {

    private final MadeDexRepository madeDexRepository;
    private final MadeDexMemberRepository madeDexMemberRepository;

    public MadeDex active(Long madeDexId) {
        return madeDexRepository.findByIdAndDeletedAtIsNull(madeDexId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_NOT_FOUND));
    }

    // 정원·그룹장 판정 전에 잠근다. 검사와 실행 사이에 끼어들면 13번째 멤버나 그룹장 둘이 생긴다
    public MadeDex locked(Long madeDexId) {
        return madeDexRepository.findActiveByIdForUpdate(madeDexId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_NOT_FOUND));
    }

    /** 열람 가능한 도감을 내 역할과 함께 돌려준다. 열람 판정은 이 메서드만 쓴다 */
    public MadeDexAccess readable(Long userId, Long madeDexId) {
        MadeDex madeDex = active(madeDexId);
        MadeDexRole myRole = madeDexMemberRepository.findByMadeDexIdAndUserId(madeDexId, userId)
                .map(MadeDexMember::getRole)
                .orElse(null);
        // 403으로 답하면 비공개 도감이 "존재한다"는 사실이 드러난다
        if (myRole == null && madeDex.getVisibility() != Visibility.PUBLIC) {
            throw new CustomException(ErrorCode.MADE_DEX_NOT_FOUND);
        }
        return new MadeDexAccess(madeDex, myRole);
    }

    public record MadeDexAccess(MadeDex madeDex, MadeDexRole myRole) {

        public boolean isMember() {
            return myRole != null;
        }
    }
}
