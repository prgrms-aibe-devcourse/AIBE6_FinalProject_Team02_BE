package com.backend_catcheat.domain.my.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.my.dto.MyProfileResponse;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 마이페이지 도메인 서비스
 * 프로필 관련 기능을 담당
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyService {
    private final UserRepository userRepository;

    // 닉네임 규칙: 2~8자, 한글/영문/숫자/밑줄만
    private static final Pattern NICKNAME_PATTERN =
            Pattern.compile("^[가-힣a-zA-Z0-9_]{2,8}$");

    /** 마이페이지 프로필 조회. 닉네임 변경 가능 여부/가능 시각을 함께 계산해 준다. */
    public MyProfileResponse getProfile(Long userId) {
        User user = findUser(userId);
        LocalDateTime updatedAt = user.getNicknameUpdatedAt();
        // 변경 이력이 없으면 즉시 가능, 있으면 마지막 변경 +1개월부터 가능
        LocalDateTime changeableAt = updatedAt == null ? null : updatedAt.plusMonths(1);
        boolean changeable = changeableAt == null || !LocalDateTime.now().isBefore(changeableAt);
        return new MyProfileResponse(user.getNickname(), changeable, changeableAt);
    }

    /**
     * 최초 닉네임 세팅 (온보딩 전)
     */
    @Transactional
    public void setInitialNickname(Long userId, String rawNickname) {
        String nickname = normalize(rawNickname);
        User user = findUser(userId);

        // 초기 세팅은 닉네임이 없을 때만 (엔티티도 방어하지만, 중복검사보다 먼저 판정)
        if (user.getNickname() != null) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_SET);
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new CustomException(ErrorCode.NICKNAME_DUPLICATED);
        }

        user.setInitialNickname(nickname);
    }

    /**
     * 닉네임 변경 (마이페이지)
     * 마지막 변경 후 1개월 이내면 엔티티가 예외를 던짐
     * 현재 닉네임과 같으면 no-op(제한 시계 소모 없음)
     * 다른 사람과 겹치면 중복 예외
     */
    @Transactional
    public void changeNickname(Long userId, String rawNickname) {
        String nickname = normalize(rawNickname);
        User user = findUser(userId);

        if (nickname.equals(user.getNickname())) {
            return; // 변화 없음 — 1개월 제한을 소모하지 않는다
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new CustomException(ErrorCode.NICKNAME_DUPLICATED);
        }

        user.changeNickname(nickname, LocalDateTime.now());
    }

    /**
     * 회원 탈퇴 (소프트 삭제)
     * 개인정보를 비식별화하고 탈퇴 시각을 남긴다.
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = findUser(userId);
        user.withdraw();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    /** 앞뒤 공백 제거 후 형식 검사. 어긋나면 NICKNAME_INVALID. */
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
