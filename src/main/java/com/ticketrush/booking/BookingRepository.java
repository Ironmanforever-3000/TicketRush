package com.ticketrush.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, Long>, BookingRepositoryCustom {
    Optional<Booking> findByUserIdAndIdempotencyKey(Long userId, UUID idempotencyKey);
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    boolean existsByIdAndUser_Id(Long bookingId, Long userId);
}
