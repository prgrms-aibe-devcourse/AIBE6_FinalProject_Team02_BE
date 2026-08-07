package com.backend_catcheat.domain.made.dto;

import java.util.List;

// 일부만 보내면 나머지 순서를 알 수 없어 보이는 슬롯 전체를 받는다
public record MadeDexSlotReorderRequestDTO(List<Long> slotIds) {}
