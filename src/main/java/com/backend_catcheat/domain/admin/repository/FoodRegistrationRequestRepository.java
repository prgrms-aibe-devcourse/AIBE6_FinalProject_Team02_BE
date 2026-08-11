package com.backend_catcheat.domain.admin.repository;

import com.backend_catcheat.domain.admin.entity.FoodRegistrationRequest;
import com.backend_catcheat.domain.admin.entity.RegistrationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FoodRegistrationRequestRepository extends JpaRepository<FoodRegistrationRequest, Long> {
    List<FoodRegistrationRequest> findByStatusOrderByCreatedAtDesc(RegistrationRequestStatus status);

    /** 이 유저가 해당 상태로 걸어 둔 요청이 가리키는 도감 칸들 조회 */
    @Query("""
            select card.slotId
            from FoodRegistrationRequest request, CollectionCard card, Registration registration
            where request.collectionCardId = card.id
              and card.registrationId = registration.id
              and registration.userId = :userId
              and request.status = :status
            """)
    List<Long> findSlotIdsByUserIdAndStatus(@Param("userId") Long userId,
                                            @Param("status") RegistrationRequestStatus status);
}
