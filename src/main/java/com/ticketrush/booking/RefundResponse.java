package com.ticketrush.booking;

import java.time.OffsetDateTime;

public record RefundResponse(
        Long refundId,
        Long bookingId,
        Long userId,
        long amountCents,
        String status,
        String reason,
        OffsetDateTime createdAt
) {
    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getBooking().getId(),
                refund.getUser().getId(),
                refund.getAmountCents(),
                refund.getStatus().name(),
                refund.getReason(),
                refund.getCreatedAt()
        );
    }
}
