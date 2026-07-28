package com.backend_catcheat.domain.registration.repository;

import com.backend_catcheat.domain.registration.entity.VerificationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VerificationAttemptRepository extends JpaRepository<VerificationAttempt, Long> {

    List<VerificationAttempt> findByRegistrationIdOrderByIdAsc(Long registrationId);
}
