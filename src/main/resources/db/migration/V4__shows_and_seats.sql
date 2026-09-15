CREATE TABLE shows (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    venue_id BIGINT NOT NULL REFERENCES venues(id),
    starts_at TIMESTAMP NOT NULL,
    sale_opens_at TIMESTAMP NOT NULL,
    sale_closes_at TIMESTAMP,
    status VARCHAR(30) NOT NULL
);

CREATE TABLE seat_tiers (
    id BIGSERIAL PRIMARY KEY,
    show_id BIGINT NOT NULL REFERENCES shows(id),
    name VARCHAR(100) NOT NULL,
    price_cents BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    CONSTRAINT uq_seat_tier_name UNIQUE (show_id, name)
);

CREATE TABLE seats (
    id BIGSERIAL PRIMARY KEY,
    show_id BIGINT NOT NULL REFERENCES shows(id),
    tier_id BIGINT NOT NULL REFERENCES seat_tiers(id),
    row_label VARCHAR(20) NOT NULL,
    seat_number INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    held_by UUID,
    hold_expires_at TIMESTAMP,
    version BIGINT NOT NULL,
    CONSTRAINT uq_show_seat UNIQUE (show_id, row_label, seat_number)
);
