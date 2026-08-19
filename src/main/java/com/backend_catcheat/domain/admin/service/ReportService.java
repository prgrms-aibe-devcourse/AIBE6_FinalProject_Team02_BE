package com.backend_catcheat.domain.admin.service;

import com.backend_catcheat.domain.admin.dto.FoodReportResponseDTO;
import com.backend_catcheat.domain.admin.entity.ReportStatus;
import com.backend_catcheat.domain.admin.entity.UnidentifiedFoodReport;
import com.backend_catcheat.domain.admin.repository.UnidentifiedFoodReportRepository;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.global.event.FoodReportApprovedEvent;
import com.backend_catcheat.global.event.FoodReportRejectedEvent;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 미확인 음식 제보 도메인 서비스.
 *  - 생성(사용자): createReport
 *  - 처리(관리자): 목록 조회 / 채택 / 반려
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int NAME_MAX_LENGTH = 200;

    private final UnidentifiedFoodReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 사용자 제보 생성 — 도감에 없는 음식 이름을 PENDING으로 쌓는다. */
    @Transactional
    public FoodReportResponseDTO createReport(Long reporterId, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
            throw new CustomException(ErrorCode.REPORT_NAME_REQUIRED);
        }
        UnidentifiedFoodReport saved = reportRepository.save(
                UnidentifiedFoodReport.builder()
                        .description(trimmed)
                        .reporterId(reporterId)
                        .build());
        return toResponse(saved);
    }

    /** 대기 중(PENDING) 제보 목록을 최신순으로. */
    @Transactional(readOnly = true)
    public List<FoodReportResponseDTO> getReports(ReportStatus status) {
        return reportRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** 제보 채택 (→ 신규 도감 칸 생성 대상). */
    @Transactional
    public void acceptReport(Long adminId, Long reportId) {
        UnidentifiedFoodReport report = loadPending(reportId);
        report.accept();   // 더티 체킹 → 자동 UPDATE

        Long reporterId = report.getReporterId();
        if (reporterId != null && !reporterId.equals(adminId)) {
            eventPublisher.publishEvent(new FoodReportApprovedEvent(reportId, adminId, reporterId));
        }
    }

    /** 제보 반려 (사유 포함). */
    @Transactional
    public void rejectReport(Long adminId, Long reportId, String reason) {
        UnidentifiedFoodReport report = loadPending(reportId);
        report.reject(reason);

        Long reporterId = report.getReporterId();
        if (reporterId != null && !reporterId.equals(adminId)) {
            eventPublisher.publishEvent(new FoodReportRejectedEvent(reportId, adminId, reporterId));
        }
    }

    private UnidentifiedFoodReport loadPending(Long reportId) {
        UnidentifiedFoodReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new CustomException(ErrorCode.ADMIN_ITEM_ALREADY_HANDLED);   // 중복 처리 방지
        }
        return report;
    }

    /** 엔티티 → 응답 DTO. 제보자 이름(닉네임, 없으면 이메일)까지 채운다. */
    private FoodReportResponseDTO toResponse(UnidentifiedFoodReport r) {
        String reporterName = r.getReporterId() == null ? null
                : userRepository.findById(r.getReporterId())
                        .map(u -> u.getNickname() != null ? u.getNickname() : u.getEmail())
                        .orElse(null);
        return new FoodReportResponseDTO(
                r.getId(), r.getRegistrationId(), r.getDescription(),
                r.getStatus(), r.getCreatedAt(), r.getReporterId(), reporterName);
    }
}
