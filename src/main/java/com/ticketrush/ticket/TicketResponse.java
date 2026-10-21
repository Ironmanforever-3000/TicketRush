package com.ticketrush.ticket;

import java.util.UUID;

public record TicketResponse(
        UUID ticketId,
        String seat,
        String tier,
        TicketStatus status,
        String code
) {}
