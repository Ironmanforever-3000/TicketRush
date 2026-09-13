package com.ticketrush.hold;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface HoldRepository extends JpaRepository<Hold, Long> {
    List<Hold> findByUserIdAndShowIdAndStatusAndExpiresAtAfter(Long userId, Long showId, HoldStatus status, OffsetDateTime time);
}
