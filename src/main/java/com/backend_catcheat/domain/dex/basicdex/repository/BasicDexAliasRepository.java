package com.backend_catcheat.domain.dex.basicdex.repository;

import com.backend_catcheat.domain.dex.basicdex.entity.BasicDexAliasEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BasicDexAliasRepository extends JpaRepository<BasicDexAliasEntity, Long> {
}
