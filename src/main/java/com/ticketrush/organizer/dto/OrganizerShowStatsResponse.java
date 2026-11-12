package com.ticketrush.organizer.dto;

public record OrganizerShowStatsResponse(
        Long showId,
        Long eventId,
        String eventTitle,
        long totalSeats,
        long availableSeats,
        long heldSeats,
        long soldSeats,
        long confirmedBookings,
        long cancelledBookings,
        long confirmedRevenueCents,
        long validTickets,
        long usedTickets,
        long cancelledTickets,
        long organizerTotalTicketsSold,
        long organizerTotalRevenueCents
) {
}
