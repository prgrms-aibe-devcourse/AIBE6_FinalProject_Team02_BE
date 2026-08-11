package com.backend_catcheat.domain.made.dto;

import java.time.LocalDate;
import java.util.List;

// date는 조회한 날, today는 서버(Asia/Seoul)의 기준일이다. 둘을 한 필드로 합치면
// 과거를 조회하는 순간 클라이언트가 오늘을 잃는다
public record MadeDexFeedDTO(
        LocalDate date,
        LocalDate today,
        List<MadeDexFeedSlotDTO> slots
) {}
