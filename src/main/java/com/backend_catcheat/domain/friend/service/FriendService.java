package com.backend_catcheat.domain.friend.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.badge.dto.EquippedBadgeViewDTO;
import com.backend_catcheat.domain.badge.service.EquippedBadgeResolver;
import com.backend_catcheat.domain.friend.dto.ReceivedRequestDTO;
import com.backend_catcheat.domain.friend.entity.Friendship;
import com.backend_catcheat.domain.friend.entity.type.FriendshipStatus;
import com.backend_catcheat.domain.friend.repository.FriendshipRepository;
import com.backend_catcheat.domain.friend.entity.type.RelationStatus;
import com.backend_catcheat.domain.user.dto.UserBriefDTO;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendService {
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final EquippedBadgeResolver equippedBadgeResolver;
    private final S3PresignedUrlService s3PresignedUrlService;

    /** 나와 상대의 관계 판정 */
    public RelationStatus relationOf(Long meId, Long otherId) {
        if (meId.equals(otherId)) {
            return RelationStatus.SELF;
        }
        return friendshipRepository.findBetween(meId, otherId)
                .map(f -> toRelation(meId, f))
                .orElse(RelationStatus.NONE);
    }

    /** 여러 상대의 관계 배치 판정(검색용) */
    public Map<Long, RelationStatus> relationsOf(Long meId, Collection<Long> others) {
        Map<Long, Friendship> byOther = friendshipRepository.findAllBetween(meId, others).stream()
                .collect(Collectors.toMap(f -> f.getRequesterId().equals(meId)
                        ? f.getAddresseeId() : f.getRequesterId(), Function.identity()));
        return others.stream().collect(Collectors.toMap(Function.identity(),
                id -> id.equals(meId) ? RelationStatus.SELF
                        : byOther.containsKey(id) ? toRelation(meId, byOther.get(id))
                        : RelationStatus.NONE));
    }

    private RelationStatus toRelation(Long meId, Friendship f) {
        if (f.isAccepted()) {
            return RelationStatus.FRIEND;
        }
        // PENDING: 내가 요청자면 보냄, 아니면 받음
        return f.getRequesterId().equals(meId)
                ? RelationStatus.REQUEST_SENT : RelationStatus.REQUEST_RECEIVED;
    }

    /** 친구 요청 */
    @Transactional
    public void sendRequest(Long meId, Long targetUserId) {
        if (meId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.FRIEND_SELF_NOT_ALLOWED);
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        friendshipRepository.findBetween(meId, targetUserId).ifPresentOrElse(existing -> {
            if (existing.isAccepted()) {
                throw new CustomException(ErrorCode.FRIEND_ALREADY);
            }
            if (existing.getRequesterId().equals(meId)) {
                throw new CustomException(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
            }
            // 상대가 이미 나에게 보낸 PENDING → 자동 수락
            existing.accept(LocalDateTime.now());
        }, () -> friendshipRepository.save(Friendship.request(meId, targetUserId)));
    }

    /** 받은 요청 수락 */
    @Transactional
    public void accept(Long meId, Long requestId) {
        Friendship f = getPendingOr404(requestId);
        if (!f.getAddresseeId().equals(meId)) {
            throw new CustomException(ErrorCode.FRIEND_FORBIDDEN);
        }
        f.accept(LocalDateTime.now());
    }

    /** 요청 삭제 */
    @Transactional
    public void deleteRequest(Long meId, Long requestId) {
        Friendship f = getPendingOr404(requestId);
        if (!f.getAddresseeId().equals(meId) && !f.getRequesterId().equals(meId)) {
            throw new CustomException(ErrorCode.FRIEND_FORBIDDEN);
        }
        friendshipRepository.delete(f);
    }

    /** 친구 삭제 */
    @Transactional
    public void removeFriend(Long meId, Long otherUserId) {
        Friendship f = friendshipRepository.findBetween(meId, otherUserId)
                .filter(Friendship::isAccepted)
                .orElseThrow(() -> new CustomException(ErrorCode.FRIEND_NOT_FOUND));
        friendshipRepository.delete(f);
    }

    /** 내 친구 목록 */
    public List<UserBriefDTO> listFriends(Long meId) {
        List<Long> friendIds = friendshipRepository
                .findAllByStatusAndMember(meId, FriendshipStatus.ACCEPTED).stream()
                .map(f -> f.getRequesterId().equals(meId) ? f.getAddresseeId() : f.getRequesterId())
                .toList();
        return userRepository.findAllById(friendIds).stream().map(this::toBrief).toList();
    }

    /** 받은/보낸 요청 목록 */
    public List<ReceivedRequestDTO> listRequests(Long meId, boolean received) {
        List<Friendship> rows = received
                ? friendshipRepository.findByAddresseeIdAndStatus(meId, FriendshipStatus.PENDING)
                : friendshipRepository.findByRequesterIdAndStatus(meId, FriendshipStatus.PENDING);
        return rows.stream().map(f -> {
            Long otherId = received ? f.getRequesterId() : f.getAddresseeId();
            User other = userRepository.findById(otherId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            return new ReceivedRequestDTO(f.getId(), toBrief(other));
        }).toList();
    }

    /** 프로필사진 프리사인 + 대표뱃지 */
    public UserBriefDTO toBrief(User user) {
        EquippedBadgeViewDTO badge = equippedBadgeResolver.resolve(user);
        return new UserBriefDTO(
                user.getId(),
                user.getNickname(),
                s3PresignedUrlService.createDownloadUrl(user.getProfileImageKey()),
                badge);
    }

    private Friendship getPendingOr404(Long requestId) {
        Friendship f = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));
        if (f.getStatus() != FriendshipStatus.PENDING) {
            throw new CustomException(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }
        return f;
    }
}
