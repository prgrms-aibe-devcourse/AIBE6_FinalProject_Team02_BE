package com.backend_catcheat.domain.upload.dto;

import java.util.List;

public record PresignedUploadResponseDTO (
        List<UploadTarget> uploads
){
    public record UploadTarget(
            String key,
            String uploadUrl,
            String publicUrl
    ) {}
}
