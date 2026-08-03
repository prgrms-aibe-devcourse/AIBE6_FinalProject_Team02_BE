package com.backend_catcheat.domain.made.repository;

import com.backend_catcheat.domain.made.entity.MadeDex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MadeDexRepository extends JpaRepository<MadeDex, Long> {

    List<MadeDex> findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(Collection<Long> ids);
}
