package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.made.dto.MadeDexLeaveResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexMemberDTO;
import com.backend_catcheat.domain.made.dto.MadeDexMembersResponseDTO;
import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.repository.MadeDexInviteRepository;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MadeDexMemberService {

    private final MadeDexFinder madeDexFinder;
    private final MadeDexMemberRepository madeDexMemberRepository;
    private final MadeDexInviteRepository madeDexInviteRepository;
    private final UserRepository userRepository;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final Clock clock;

    public MadeDexMembersResponseDTO findMembers(Long userId, Long madeDexId) {
        MadeDex madeDex = madeDexFinder.active(madeDexId);
        MadeDexMember me = requireMember(madeDexId, userId);

        List<MadeDexMember> members = madeDexMemberRepository
                .findByMadeDexIdOrderByJoinedAtAscIdAsc(madeDexId);

        // 닉네임·사진은 users에 있다. 한 번에 읽어 N+1을 만들지 않는다
        Map<Long, User> userById = userRepository
                .findAllById(members.stream().map(MadeDexMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // 정렬이 안정적이라 그룹장을 끌어올려도 나머지 가입 순서는 그대로다
        List<MadeDexMemberDTO> rows = members.stream()
                .sorted(Comparator.comparing(MadeDexMember::isOwner).reversed())
                .map(member -> toDTO(member, userById.get(member.getUserId()), userId))
                .toList();

        return new MadeDexMembersResponseDTO(
                madeDex.getId(), madeDex.getName(), madeDex.getMaxMembers(), me.getRole(), rows);
    }

    @Transactional
    public void kick(Long ownerId, Long madeDexId, Long targetUserId) {
        // 위임·탈퇴와 같은 행을 잠근다. 그래야 "누가 그룹장인가"에 두 요청이 동시에 답하지 않는다
        MadeDex madeDex = madeDexFinder.locked(madeDexId);
        madeDex.requireOwner(ownerId);

        // 그룹장이 스스로를 내보내면 주인 없는 방이 된다
        if (ownerId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.MADE_DEX_CANNOT_KICK_SELF);
        }
        if (madeDexMemberRepository.deleteByMadeDexIdAndUserId(madeDexId, targetUserId) == 0) {
            throw new CustomException(ErrorCode.MADE_DEX_MEMBER_NOT_FOUND);
        }
    }

    @Transactional
    public MadeDexLeaveResponseDTO leave(Long userId, Long madeDexId) {
        MadeDex madeDex = madeDexFinder.locked(madeDexId);
        requireMember(madeDexId, userId);

        if (!madeDex.isOwner(userId)) {
            madeDexMemberRepository.deleteByMadeDexIdAndUserId(madeDexId, userId);
            return new MadeDexLeaveResponseDTO(false);
        }

        // 그룹장이 나가면 방이 사라진다
        LocalDateTime now = LocalDateTime.now(clock);
        madeDex.delete(now);
        // 남은 코드를 죽이지 않으면 참여 시도가 "없는 도감"으로 끝난다
        madeDexInviteRepository.revokeActive(madeDexId, now);
        return new MadeDexLeaveResponseDTO(true);
    }

    @Transactional
    public void transferOwner(Long ownerId, Long madeDexId, Long targetUserId) {
        MadeDex madeDex = madeDexFinder.locked(madeDexId);
        madeDex.requireOwner(ownerId);

        if (ownerId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.MADE_DEX_ALREADY_OWNER);
        }

        MadeDexMember current = requireMember(madeDexId, ownerId);
        MadeDexMember next = madeDexMemberRepository
                .findByMadeDexIdAndUserId(madeDexId, targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_MEMBER_NOT_FOUND));

        // 주인이 made_dex.owner_id와 member.role 두 곳에 적혀 있어 항상 함께 옮긴다
        current.demoteToMember();
        next.promoteToOwner();
        madeDex.changeOwner(targetUserId);
    }

    private MadeDexMemberDTO toDTO(MadeDexMember member, User user, Long viewerId) {
        return new MadeDexMemberDTO(
                member.getUserId(),
                user == null ? null : user.getNickname(),
                user == null ? null : s3PresignedUrlService.createDownloadUrl(user.getProfileImageKey()),
                member.getRole(),
                member.getJoinedAt(),
                member.getUserId().equals(viewerId));
    }

    private MadeDexMember requireMember(Long madeDexId, Long userId) {
        return madeDexMemberRepository.findByMadeDexIdAndUserId(madeDexId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_NOT_MEMBER));
    }
}
