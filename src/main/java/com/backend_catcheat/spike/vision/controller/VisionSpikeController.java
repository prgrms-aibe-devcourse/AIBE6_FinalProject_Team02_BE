package com.backend_catcheat.spike.vision.controller;

import com.backend_catcheat.global.common.ApiResponse;
import com.backend_catcheat.spike.vision.dto.VisionAnalysisResponse;
import com.backend_catcheat.spike.vision.service.VisionSpikeService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Profile("dev")
@RestController
@RequestMapping("/api/v1/spike/vision")
public class VisionSpikeController {

    private final VisionSpikeService visionSpikeService;

    public VisionSpikeController(VisionSpikeService visionSpikeService) {
        this.visionSpikeService = visionSpikeService;
    }


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<VisionAnalysisResponse> analyze(
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "hint", required = false) String hint
    ) {
        return ApiResponse.ok(visionSpikeService.analyze(images, hint));
    }
}
