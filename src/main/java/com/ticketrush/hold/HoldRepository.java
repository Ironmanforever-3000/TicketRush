package com.ticketrush.hold;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface HoldRepository extends JpaRepository<Hold, Long> {

    @Query("SELECT h FROM Hold h WHERE h.user.id = :userId AND h.show.id = :showId AND h.status = :status AND h.expiresAt > :time")
    List<Hold> findByUserIdAndShowIdAndStatusAndExpiresAtAfter(
            @Param("userId") Long userId,
            @Param("showId") Long showId,
            @Param("status") HoldStatus status,
            @Param("time") OffsetDateTime time
    );

    @Query("SELECT COUNT(h) FROM Hold h WHERE h.user.id = :userId AND h.status = com.ticketrush.hold.HoldStatus.ACTIVE AND h.expiresAt > :now")
    long countActiveHolds(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Hold h SET h.status = com.ticketrush.hold.HoldStatus.EXPIRED WHERE h.status = com.ticketrush.hold.HoldStatus.ACTIVE AND h.expiresAt <= :now")
    int expireOldHolds(@Param("now") OffsetDateTime now);

    @Query("SELECT COUNT(h) FROM Hold h WHERE h.status = com.ticketrush.hold.HoldStatus.ACTIVE AND h.expiresAt > :now")
    long countAllActive(@Param("now") OffsetDateTime now);
}
