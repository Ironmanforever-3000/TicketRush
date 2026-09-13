package com.ticketrush.seat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByShowIdOrderByRowLabelAscSeatNumberAsc(Long showId);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Seat s
           SET s.status = :status,
               s.heldBy = :userId,
               s.holdExpiresAt = :expiresAt,
               s.version = s.version + 1
         WHERE s.id IN :seatIds
           AND s.show.id = :showId
           AND s.status = :availableStatus
        """)
    int holdAvailableSeats(
            @Param("seatIds") List<Long> seatIds,
            @Param("showId") Long showId,
            @Param("userId") Long userId,
            @Param("expiresAt") OffsetDateTime expiresAt,
            @Param("availableStatus") SeatStatus availableStatus,
            @Param("status") SeatStatus status
    );

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE seats
        SET
            status = 'AVAILABLE',
            held_by = NULL,
            hold_expires_at = NULL,
            version = version + 1
        WHERE status = 'HELD'
          AND hold_expires_at < now()
        RETURNING id
        """, nativeQuery = true)
    List<Long> releaseExpiredHolds();

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Seat s
           SET s.status = com.ticketrush.seat.SeatStatus.SOLD,
               s.version = s.version + 1
         WHERE s.id IN :seatIds
           AND s.show.id = :showId
           AND s.status = com.ticketrush.seat.SeatStatus.HELD
           AND s.heldBy = :userId
           AND s.holdExpiresAt > CURRENT_TIMESTAMP
    """)
    int confirmHeldSeats(
        @Param("seatIds") List<Long> seatIds,
        @Param("showId") Long showId,
        @Param("userId") Long userId
    );
}
