package com.backend_catcheat.domain.admin.repository;

import com.backend_catcheat.domain.admin.entity.FoodRegistrationRequest;
import com.backend_catcheat.domain.admin.entity.ResistrationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodRegistationRequestRepository extends JpaRepository<FoodRegistrationRequest, Long> {
    List<FoodRegistrationRequest> findByStatusOrderByCreateAtDesc(ResistrationRequestStatus status);
}
