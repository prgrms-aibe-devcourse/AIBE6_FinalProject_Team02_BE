package com.backend_catcheat.domain.my.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * 마이페이지 도메인 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyService {
    private final UserRepository userRepository;

    // 닉네임 규칙: 2~8자, 한글/영문/숫자/밑줄만
    private static final Pattern NICKNAME_PATTERN =
            Pattern.compile("^[가-힣a-zA-Z0-9_]{2,8}$");

    /**
     * 최초 닉네임 세팅 (온보딩 전)
     */
    @Transactional
    public void setInitialNickname(Long userId, String rawNickname) {
        String nickname = normalize(rawNickname);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 초기 세팅은 닉네임이 없을 때만 (엔티티도 방어하지만, 중복검사보다 먼저 판정)
        if (user.getNickname() != null) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_SET);
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new CustomException(ErrorCode.NICKNAME_DUPLICATED);
        }

        user.setInitialNickname(nickname);
    }

    /** 앞뒤 공백 제거 후 형식 검사 */
    private String normalize(String rawNickname) {
        if (rawNickname == null) {
            throw new CustomException(ErrorCode.NICKNAME_INVALID);
        }
        String nickname = rawNickname.trim();
        if (!NICKNAME_PATTERN.matcher(nickname).matches()) {
            throw new CustomException(ErrorCode.NICKNAME_INVALID);
        }
        return nickname;
    }
}
