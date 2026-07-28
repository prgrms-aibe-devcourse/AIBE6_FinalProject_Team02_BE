package com.backend_catcheat.domain.admin.repository;

import com.backend_catcheat.domain.admin.entity.ReportStatus;
import com.backend_catcheat.domain.admin.entity.UnidentifiedFoodReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnidentifiedFoodReportRepository extends JpaRepository<UnidentifiedFoodReport, Long> {
    List<UnidentifiedFoodReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);
}
