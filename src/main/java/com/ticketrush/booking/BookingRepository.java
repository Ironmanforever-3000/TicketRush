package com.ticketrush.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, Long>, BookingRepositoryCustom {

    @Query("SELECT b FROM Booking b WHERE b.user.id = :userId AND b.idempotencyKey = :idempotencyKey")
    Optional<Booking> findByUserIdAndIdempotencyKey(@Param("userId") Long userId, @Param("idempotencyKey") UUID idempotencyKey);

    @Query("SELECT b FROM Booking b WHERE b.user.id = :userId ORDER BY b.createdAt DESC")
    List<Booking> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    boolean existsByIdAndUser_Id(Long bookingId, Long userId);
}
