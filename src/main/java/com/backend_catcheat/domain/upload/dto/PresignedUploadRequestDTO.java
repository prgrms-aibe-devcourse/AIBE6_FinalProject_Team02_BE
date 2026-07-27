package com.backend_catcheat.domain.upload.dto;

import java.util.List;

public record PresignedUploadRequestDTO (
        List<FileInfo> files
){
    public record FileInfo(
            String fileName,
            String contentType
    ) {}

}
