package com.backend_catcheat.domain.made.dto;

import java.time.LocalDateTime;

/** 발급된 초대 코드. 공유 링크는 FE가 origin과 합쳐 만든다 */
public record MadeDexInviteResponseDTO(String code, LocalDateTime expiresAt) {
}
