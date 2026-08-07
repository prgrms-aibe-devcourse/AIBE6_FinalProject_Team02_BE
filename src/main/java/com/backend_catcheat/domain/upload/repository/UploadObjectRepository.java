package com.backend_catcheat.domain.upload.repository;

import com.backend_catcheat.domain.upload.entity.UploadObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UploadObjectRepository extends JpaRepository<UploadObject, String> {

    List<UploadObject> findByImageKeyIn(Collection<String> imageKeys);

    void deleteByImageKey(String imageKey);
}
