package com.backend_catcheat.domain.made.dto;

import com.backend_catcheat.domain.made.dto.MadeDexRecordCreateRequestDTO.PhotoInput;

import java.time.LocalTime;
import java.util.List;

/**
 * 사진은 유지할 것의 id와 새로 올린 key를 나눠 받는다.
 * 기존 사진을 key로 되돌려받으면 남의 key를 섞어 보낼 수 있다.
 * 최종 순서는 keepPhotos 다음에 newPhotos다.
 * 날짜(loggedOn)는 받지 않는다 — 기록이 놓인 날은 만든 뒤 바뀌지 않는다.
 */
public record MadeDexRecordUpdateRequestDTO(
        Long slotId,
        LocalTime loggedTime,
        List<KeptPhoto> keepPhotos,
        List<PhotoInput> newPhotos
) {
    /** 사진은 그대로 두고 글만 고칠 수 있어야 한다 */
    public record KeptPhoto(Long photoId, String caption, Double cropX, Double cropY) {}
}
