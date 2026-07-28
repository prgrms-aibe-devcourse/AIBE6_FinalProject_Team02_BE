package com.backend_catcheat.domain.admin.service;

import com.backend_catcheat.domain.admin.dto.FoodRegistrationRequestResponseDTO;
import com.backend_catcheat.domain.admin.dto.FoodReportResponseDTO;
import com.backend_catcheat.domain.admin.entity.FoodRegistrationRequest;
import com.backend_catcheat.domain.admin.entity.RegistrationRequestStatus;
import com.backend_catcheat.domain.admin.entity.ReportStatus;
import com.backend_catcheat.domain.admin.entity.UnidentifiedFoodReport;
import com.backend_catcheat.domain.admin.repository.FoodRegistrationRequestRepository;
import com.backend_catcheat.domain.admin.repository.UnidentifiedFoodReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UnidentifiedFoodReportRepository reportRepository;
    private final FoodRegistrationRequestRepository requestRepository;

    @Transactional
    public List<FoodReportResponseDTO> getPendingReports(){
        return reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING)
                .stream()
                .map(FoodReportResponseDTO::from)
                .collect(Collectors.toList());

    }

    @Transactional
    public void acceptReport(Long reportId) {
        UnidentifiedFoodReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> notFound("제보를 찾을 수 없습니다."));
        requirePending(report.getStatus() == ReportStatus.PENDING);
        report.accept();   // 더티 체킹 → 자동 UPDATE
    }


    @Transactional
    public void rejectReport(Long reportId, String reason) {
        UnidentifiedFoodReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> notFound("제보를 찾을 수 없습니다."));
        requirePending(report.getStatus() == ReportStatus.PENDING);
        report.reject(reason);
    }


    //=====음식 등록 요청 승인 큐=====
    @Transactional
    public List<FoodRegistrationRequestResponseDTO> getPendingRequests(){
        return requestRepository.findByStatusOrderByCreatedAtDesc(RegistrationRequestStatus.PENDING)
                .stream()
                .map(FoodRegistrationRequestResponseDTO::from)
                .toList();
    }
    /** 등록 완료 (관리자가 등록을 끝냄). dex 도메인 준비되면 슬롯 생성 연결. */
    @Transactional
    public void completeRequest(Long requestId) {
        FoodRegistrationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> notFound("등록 요청을 찾을 수 없습니다."));
        requirePending(request.getStatus() == RegistrationRequestStatus.PENDING);
        request.complete();
    }

    /** 등록 요청 반려. */
    @Transactional
    public void rejectRequest(Long requestId, String reason) {
        FoodRegistrationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> notFound("등록 요청을 찾을 수 없습니다."));
        requirePending(request.getStatus() == RegistrationRequestStatus.PENDING);
        request.reject(reason);
    }
    // ===== 공통 헬퍼 =====

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    /** PENDING이 아니면(이미 처리됨) 409로 막아 중복 처리 방지. */
    private void requirePending(boolean isPending) {
        if (!isPending) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 항목입니다.");
        }
    }

}
