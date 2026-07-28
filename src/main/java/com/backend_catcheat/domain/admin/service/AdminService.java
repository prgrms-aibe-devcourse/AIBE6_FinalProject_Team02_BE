package com.backend_catcheat.domain.admin.service;

import com.backend_catcheat.domain.admin.dto.FoodReportResponseDTO;
import com.backend_catcheat.domain.admin.entity.ReportStatus;
import com.backend_catcheat.domain.admin.entity.UnidentifiedFoodReport;
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

    @Transactional
    public List<FoodReportResponseDTO> getPendingReports(){
        return reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING)
                .stream()
                .map(FoodReportResponseDTO::from)
                .collect(Collectors.toList());

    }
    @Transactional
    public void acceptReport(Long reportId){
        UnidentifiedFoodReport report = findPendingOrThrow(reportId);
        report.accept();
    }

    @Transactional
    public void rejectReport(Long reportId){
        UnidentifiedFoodReport report = findPendingOrThrow(reportId);
        report.reject();
    }

    private UnidentifiedFoodReport findPendingOrThrow(Long reportId){
        UnidentifiedFoodReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "제보를 찾을 수 없습니다."));
        if(report.getStatus() != ReportStatus.PENDING){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 처리된 제보입니다.");
        }
        return report;
    }
}
