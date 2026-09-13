package com.ticketrush.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, Long>, BookingRepositoryCustom {
    Optional<Booking> findByIdempotencyKey(UUID idempotencyKey);
}
