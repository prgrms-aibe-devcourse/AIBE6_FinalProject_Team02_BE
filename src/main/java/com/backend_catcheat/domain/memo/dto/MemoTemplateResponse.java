package com.backend_catcheat.domain.memo.dto;

import com.backend_catcheat.domain.memo.entity.MemoTemplate;

import java.time.LocalDateTime;

public record MemoTemplateResponse(Long id, String content, LocalDateTime lastUsedAt) {

    public static MemoTemplateResponse from(MemoTemplate template) {
        return new MemoTemplateResponse(
                template.getId(), template.getContent(), template.getLastUsedAt());
    }
}
