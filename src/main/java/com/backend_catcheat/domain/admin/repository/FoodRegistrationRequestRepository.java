package com.backend_catcheat.domain.admin.repository;

import com.backend_catcheat.domain.admin.entity.FoodRegistrationRequest;
import com.backend_catcheat.domain.admin.entity.RegistrationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodRegistrationRequestRepository extends JpaRepository<FoodRegistrationRequest, Long> {
    List<FoodRegistrationRequest> findByStatusOrderByCreatedAtDesc(RegistrationRequestStatus status);
}
