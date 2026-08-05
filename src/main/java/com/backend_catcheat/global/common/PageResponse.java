package com.backend_catcheat.global.common;

import java.util.List;

// 목록 페이지 응답. content + 페이지 메타
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {}
