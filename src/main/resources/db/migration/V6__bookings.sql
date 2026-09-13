CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,
    show_id BIGINT NOT NULL,
    hold_id BIGINT NOT NULL,

    total_cents BIGINT NOT NULL,

    status VARCHAR(30) NOT NULL,

    idempotency_key UUID NOT NULL UNIQUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_bookings_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT fk_bookings_show
        FOREIGN KEY (show_id)
        REFERENCES shows(id),

    CONSTRAINT fk_bookings_hold
        FOREIGN KEY (hold_id)
        REFERENCES holds(id),

    CONSTRAINT chk_bookings_total_positive
        CHECK (total_cents > 0)
);

CREATE INDEX idx_bookings_user
    ON bookings(user_id, created_at DESC);

CREATE TABLE booking_seats (
    booking_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    price_cents BIGINT NOT NULL,

    PRIMARY KEY (booking_id, seat_id),

    CONSTRAINT fk_booking_seats_booking
        FOREIGN KEY (booking_id)
        REFERENCES bookings(id),

    CONSTRAINT fk_booking_seats_seat
        FOREIGN KEY (seat_id)
        REFERENCES seats(id),

    CONSTRAINT chk_booking_seats_price_positive
        CHECK (price_cents > 0)
);

CREATE TABLE outbox (
    id BIGSERIAL PRIMARY KEY,

    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id BIGINT NOT NULL,

    event_type VARCHAR(100) NOT NULL,

    payload JSONB NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    published_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_outbox_unpublished
    ON outbox(created_at)
    WHERE published_at IS NULL;
