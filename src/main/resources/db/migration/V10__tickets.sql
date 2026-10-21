CREATE TABLE tickets (
    id            UUID PRIMARY KEY,
    booking_id    BIGINT      NOT NULL REFERENCES bookings(id),
    show_id       BIGINT      NOT NULL REFERENCES shows(id),
    seat_id       BIGINT      NOT NULL REFERENCES seats(id),
    status        VARCHAR(16) NOT NULL DEFAULT 'VALID' CHECK (status IN ('VALID','USED','CANCELLED')),
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checked_in_at TIMESTAMP,
    checked_in_by BIGINT REFERENCES users(id),
    CONSTRAINT uq_ticket_booking_seat UNIQUE (booking_id, seat_id),
    CONSTRAINT chk_ticket_used CHECK ((status = 'USED' AND checked_in_at IS NOT NULL) OR (status <> 'USED'))
);

CREATE UNIQUE INDEX uq_ticket_active_seat ON tickets(seat_id) WHERE status <> 'CANCELLED';
CREATE INDEX idx_tickets_booking ON tickets(booking_id);
