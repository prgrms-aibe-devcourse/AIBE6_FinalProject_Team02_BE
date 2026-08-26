package com.backend_catcheat.domain.upload;

import com.backend_catcheat.domain.upload.dto.PresignedUploadRequestDTO;
import com.backend_catcheat.domain.upload.dto.PresignedUploadResponseDTO;
import com.backend_catcheat.domain.upload.dto.UploadPurpose;
import com.backend_catcheat.global.common.ApiResponse;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 용도는 경로가 정한다. 요청 본문으로 받으면 클라이언트가 자기 장수 상한을 고를 수 있다
@Tag(name = "업로드 · S3 presigned", description = """
        사진은 **서버를 거치지 않고 S3로 직접** 올라간다. 서버는 권한을 확인하고 presigned URL 만 발급한다.

        용도(purpose)는 **경로가 정한다.** 요청 본문으로 받으면 클라이언트가 자기 장수 상한을 고를 수 있기 때문이다.
        발급된 key 는 나중에 쓸 때 용도까지 대조하므로, 다른 경로로 받은 key 를 끼워 넣으면 막힌다.
        """)
@RestController
@RequestMapping("/api/v1/uploads/presigned")
@RequiredArgsConstructor
public class UploadController {

    private final S3PresignedUrlService s3PresignedUrlService;

    @Operation(summary = "업로드 URL 발급 (기본)", description = "기본 도감 등록 등 일반 용도. 장수 상한은 서버가 정한다.")
    @PostMapping
    public ApiResponse<PresignedUploadResponseDTO> createPresignedUploadUrls(
            @AuthenticationPrincipal Long userId,
            @RequestBody PresignedUploadRequestDTO request) {
        return ApiResponse.ok(s3PresignedUrlService.createUploadUrls(userId, request, UploadPurpose.DEFAULT));
    }

    /** 로그잇 식사 기록용. 기본 도감보다 많은 장수를 허용한다 */
    @Operation(summary = "업로드 URL 발급 (로그잇 기록)", description = "로그잇 식사 기록용. AI에 보내지 않아 기본 도감보다 많은 장수를 허용한다.")
    @PostMapping("/logit-records")
    public ApiResponse<PresignedUploadResponseDTO> createLogitRecordUploadUrls(
            @AuthenticationPrincipal Long userId,
            @RequestBody PresignedUploadRequestDTO request) {
        return ApiResponse.ok(s3PresignedUrlService.createUploadUrls(userId, request, UploadPurpose.LOGIT_RECORD));
    }

    /**
     * AI 일러스트의 원본 사진용. 한 장만 받는다.
     * 용도를 따로 두는 이유는 requireUsableBy가 발급 용도까지 대조하기 때문이다 —
     * 다른 경로로 받은 key를 여기에 쓰면 막힌다.
     */
    @Operation(summary = "업로드 URL 발급 (일러스트 원본)", description = "AI 일러스트의 원본 사진용. **한 장만** 받는다.")
    @PostMapping("/illustrations")
    public ApiResponse<PresignedUploadResponseDTO> createIllustrationSourceUploadUrls(
            @AuthenticationPrincipal Long userId,
            @RequestBody PresignedUploadRequestDTO request) {
        return ApiResponse.ok(
                s3PresignedUrlService.createUploadUrls(userId, request, UploadPurpose.ILLUSTRATION_SOURCE));
    }

}
