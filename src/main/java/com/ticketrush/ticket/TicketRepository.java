package com.ticketrush.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE tickets
           SET status = 'USED', checked_in_at = CURRENT_TIMESTAMP, checked_in_by = :staffId
         WHERE id = :ticketId AND show_id = :showId AND status = 'VALID'
        """, nativeQuery = true)
    int markUsed(@Param("ticketId") UUID ticketId, @Param("showId") Long showId, @Param("staffId") Long staffId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Ticket t set t.status = com.ticketrush.ticket.TicketStatus.CANCELLED " +
           "where t.bookingId = :bookingId and t.status = com.ticketrush.ticket.TicketStatus.VALID")
    int cancelValidByBooking(@Param("bookingId") Long bookingId);

    List<Ticket> findByBookingId(Long bookingId);

    @Query("""
        select t from Ticket t
         where t.id = :ticketId
           and exists (select 1 from Booking b where b.id = t.bookingId and b.user.id = :userId)
        """)
    Optional<Ticket> findByIdAndOwner(@Param("ticketId") UUID ticketId, @Param("userId") Long userId);
}
