package com.backend_catcheat.domain.admin.repository;

import com.backend_catcheat.domain.admin.entity.FoodRegistrationRequest;
import com.backend_catcheat.domain.admin.entity.RegistrationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodRegistationRequestRepository extends JpaRepository<FoodRegistrationRequest, Long> {
    List<FoodRegistrationRequest> findByStatusOrderByCreateAtDesc(RegistrationRequestStatus status);
}
