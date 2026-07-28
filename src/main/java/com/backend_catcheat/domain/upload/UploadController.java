package com.backend_catcheat.domain.upload;

import com.backend_catcheat.domain.upload.dto.PresignedUploadRequestDTO;
import com.backend_catcheat.domain.upload.dto.PresignedUploadResponseDTO;
import com.backend_catcheat.global.common.ApiResponse;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uploads/presigned")
@RequiredArgsConstructor
public class UploadController {

    private final S3PresignedUrlService s3PresignedUrlService;

    @PostMapping
    public ApiResponse<PresignedUploadResponseDTO> createPresignedUploadUrls(@RequestBody PresignedUploadRequestDTO request) {
        return ApiResponse.ok(s3PresignedUrlService.createUploadUrls(request));
    }

}
