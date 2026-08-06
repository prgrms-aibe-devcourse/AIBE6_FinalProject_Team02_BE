package com.backend_catcheat.domain.made.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 초대 코드 생성기.
 * 사람이 눈으로 읽고 손으로 옮겨 적는 값이라 헷갈리는 글자(0/O, 1/I/L)를 뺐다.
 * 남은 31글자 6자리 = 약 8.9억 조합. 중복은 uk_made_dex_invite_code가 최종 방어선이다.
 */
@Component
public class InviteCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
