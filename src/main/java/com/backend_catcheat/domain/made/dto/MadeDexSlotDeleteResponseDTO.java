package com.backend_catcheat.domain.made.dto;

// 기록이 있으면 지워지지 않고 숨겨지므로, 화면이 이 값으로 문구를 가른다
public record MadeDexSlotDeleteResponseDTO(boolean hidden) {}
