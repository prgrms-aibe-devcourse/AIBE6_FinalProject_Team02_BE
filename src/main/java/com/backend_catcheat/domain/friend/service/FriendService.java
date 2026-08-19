package com.backend_catcheat.domain.friend.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.badge.dto.EquippedBadgeViewDTO;
import com.backend_catcheat.domain.badge.service.EquippedBadgeResolver;
import com.backend_catcheat.domain.friend.dto.ReceivedRequestDTO;
import com.backend_catcheat.domain.friend.entity.Friendship;
import com.backend_catcheat.domain.friend.entity.type.FriendshipStatus;
import com.backend_catcheat.domain.friend.entity.type.RelationStatus;
import com.backend_catcheat.domain.friend.repository.FriendshipRepository;
import com.backend_catcheat.domain.user.dto.UserBriefDTO;
import com.backend_catcheat.global.event.FriendRequestAcceptedEvent;
import com.backend_catcheat.global.event.FriendRequestRejectedEvent;
import com.backend_catcheat.global.event.FriendRequestSentEvent;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final ApplicationEventPublisher eventPublisher;

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
        if (targetUserId == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (meId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.FRIEND_SELF_NOT_ALLOWED);
        }
        // 탈퇴 유저에겐 요청 불가
        userRepository.findById(targetUserId)
                .filter(u -> !u.isWithdrawn())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        friendshipRepository.findBetween(meId, targetUserId).ifPresentOrElse(existing -> {
            if (existing.isAccepted()) {
                throw new CustomException(ErrorCode.FRIEND_ALREADY);
            }
            if (existing.getRequesterId().equals(meId)) {
                throw new CustomException(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
            }
            // 상대가 이미 나에게 보낸 PENDING → 자동 수락
            existing.accept(LocalDateTime.now());

            // 알림을 eventPublisher 로 처리 -> 이미 상대가 나에게 pending을 보내면 자동수락하며 알림도 수락으로 전송
            eventPublisher.publishEvent(new FriendRequestAcceptedEvent(
                    existing.getId(),
                    meId,
                    existing.getRequesterId()
            ));

        }, () -> {
            try {
                Friendship saved = friendshipRepository.saveAndFlush(Friendship.request(meId, targetUserId));

                // 새로운 요청에 대한 알림 전송
                eventPublisher.publishEvent(new FriendRequestSentEvent(
                        saved.getId(),
                        meId,
                        targetUserId
                ));

            } catch (DataIntegrityViolationException e) {
                // 유일 인덱스 위반 = 동시 요청 경쟁에서 상대가 먼저 만든 경우
                throw new CustomException(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
            }
        });
    }

    /** 받은 요청 수락 */
    @Transactional
    public void accept(Long meId, Long requestId) {
        Friendship f = getPendingOr404(requestId);
        if (!f.getAddresseeId().equals(meId)) {
            throw new CustomException(ErrorCode.FRIEND_FORBIDDEN);
        }
        f.accept(LocalDateTime.now());

        eventPublisher.publishEvent(new FriendRequestAcceptedEvent(
                f.getId(),
                meId,
                f.getRequesterId()
        ));
    }

    /** 요청 삭제 */
    @Transactional
    public void deleteRequest(Long meId, Long requestId) {

        Friendship f = getPendingOr404(requestId);

        if (!f.getAddresseeId().equals(meId) && !f.getRequesterId().equals(meId)) {
            throw new CustomException(ErrorCode.FRIEND_FORBIDDEN);
        }

        friendshipRepository.delete(f);

        if(f.getAddresseeId().equals(meId)) {
            eventPublisher.publishEvent(new FriendRequestRejectedEvent(
                    f.getId(),
                    meId,
                    f.getRequesterId()
            ));
        }

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
        return toBriefs(userRepository.findAllById(friendIds));
    }

    /** 받은/보낸 요청 목록 */
    public List<ReceivedRequestDTO> listRequests(Long meId, boolean received) {
        List<Friendship> rows = received
                ? friendshipRepository.findByAddresseeIdAndStatus(meId, FriendshipStatus.PENDING)
                : friendshipRepository.findByRequesterIdAndStatus(meId, FriendshipStatus.PENDING);
        List<Long> otherIds = rows.stream()
                .map(f -> received ? f.getRequesterId() : f.getAddresseeId())
                .toList();
        // 유저 + 대표뱃지 배치 조회 후 매핑
        Map<Long, UserBriefDTO> briefById = toBriefs(userRepository.findAllById(otherIds)).stream()
                .collect(Collectors.toMap(UserBriefDTO::userId, Function.identity()));
        return rows.stream().map(f -> {
            Long otherId = received ? f.getRequesterId() : f.getAddresseeId();
            UserBriefDTO brief = briefById.get(otherId);
            if (brief == null) {
                throw new CustomException(ErrorCode.USER_NOT_FOUND);
            }
            return new ReceivedRequestDTO(f.getId(), brief);
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

    /** 여러 유저 배치 변환. 대표뱃지를 한 번에 조회 */
    public List<UserBriefDTO> toBriefs(List<User> users) {
        Map<Long, EquippedBadgeViewDTO> badgeById = equippedBadgeResolver.resolveByBadgeId(users);
        return users.stream()
                .map(u -> new UserBriefDTO(
                        u.getId(),
                        u.getNickname(),
                        s3PresignedUrlService.createDownloadUrl(u.getProfileImageKey()),
                        u.getEquippedBadgeId() == null ? null : badgeById.get(u.getEquippedBadgeId())))
                .toList();
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
