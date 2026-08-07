package com.backend_catcheat.domain.user.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.friend.service.FriendService;
import com.backend_catcheat.domain.friend.entity.type.RelationStatus;
import com.backend_catcheat.domain.user.dto.PublicProfileDTO;
import com.backend_catcheat.domain.user.dto.UserBriefDTO;
import com.backend_catcheat.domain.user.dto.UserSearchResultDTO;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {
    private static final int SEARCH_LIMIT = 20;

    private final UserRepository userRepository;
    private final FriendService friendService;

    /** 닉네임 검색(본인 제외) + 관계상태 */
    public List<UserSearchResultDTO> search(Long meId, String nickname) {
        if (!StringUtils.hasText(nickname)) {
            return List.of();
        }
        List<User> users = userRepository.searchByNicknameContaining(
                nickname.trim(), meId, PageRequest.of(0, SEARCH_LIMIT));
        Map<Long, RelationStatus> relations = friendService.relationsOf(
                meId, users.stream().map(User::getId).toList());
        List<UserBriefDTO> briefs = friendService.toBriefs(users);
        return briefs.stream()
                .map(b -> new UserSearchResultDTO(b,
                        relations.getOrDefault(b.userId(), RelationStatus.NONE)))
                .toList();
    }

    /** 공개 프로필 */
    public PublicProfileDTO getProfile(Long meId, Long targetUserId) {
        User target = userRepository.findById(targetUserId)
                .filter(u -> !u.isWithdrawn())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        UserBriefDTO brief = friendService.toBrief(target);
        return new PublicProfileDTO(brief, friendService.relationOf(meId, targetUserId));
    }
}
