package com.backend_catcheat.domain.memo.repository;

import com.backend_catcheat.domain.memo.entity.MemoTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemoTemplateRepository extends JpaRepository<MemoTemplate, Long> {

    List<MemoTemplate> findByUserIdOrderByLastUsedAtDesc(Long userId);

    /** 같은 문구를 또 저장하면 새로 만들지 않고 이것을 재사용한다 */
    Optional<MemoTemplate> findByUserIdAndContent(Long userId, String content);

    long countByUserId(Long userId);
}
