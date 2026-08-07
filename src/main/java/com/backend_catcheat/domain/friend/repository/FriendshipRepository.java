package com.backend_catcheat.domain.friend.repository;

import com.backend_catcheat.domain.friend.entity.Friendship;
import com.backend_catcheat.domain.friend.entity.type.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    /** 두 유저 사이의 관계 행 */
    @Query("select f from Friendship f "
            + "where (f.requesterId = :a and f.addresseeId = :b) "
            + "   or (f.requesterId = :b and f.addresseeId = :a)")
    Optional<Friendship> findBetween(@Param("a") Long a, @Param("b") Long b);

    /** 나와 여러 상대의 관계 행 */
    @Query("select f from Friendship f "
            + "where (f.requesterId = :me and f.addresseeId in :others) "
            + "   or (f.addresseeId = :me and f.requesterId in :others)")
    List<Friendship> findAllBetween(@Param("me") Long me, @Param("others") Collection<Long> others);

    /** 내 친구 */
    @Query("select f from Friendship f "
            + "where (f.requesterId = :me or f.addresseeId = :me) and f.status = :status")
    List<Friendship> findAllByStatusAndMember(@Param("me") Long me,
                                              @Param("status") FriendshipStatus status);

    /** 받은 요청 */
    List<Friendship> findByAddresseeIdAndStatus(Long addresseeId, FriendshipStatus status);

    /** 보낸 요청 */
    List<Friendship> findByRequesterIdAndStatus(Long requesterId, FriendshipStatus status);
}
