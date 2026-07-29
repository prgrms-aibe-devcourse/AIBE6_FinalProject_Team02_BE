package com.backend_catcheat.domain.registration.repository;

import com.backend_catcheat.domain.registration.entity.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, Long> {

    boolean existsByHash(String hash);

    List<Photo> findByRegistrationId(Long registrationId);
}
