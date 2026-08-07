package com.backend_catcheat.domain.upload.dto;

import java.util.List;

public record PresignedUploadRequestDTO (
        List<FileInfo> files,
        UploadPurpose purpose
){
    public record FileInfo(
            String fileName,
            String contentType
    ) {}

}
