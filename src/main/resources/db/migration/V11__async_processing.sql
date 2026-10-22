ALTER TABLE outbox ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;
DROP INDEX IF EXISTS idx_outbox_unpublished;
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished ON outbox (id) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    consumer_group VARCHAR(64) NOT NULL,
    event_id       VARCHAR(64) NOT NULL,
    processed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_group, event_id)
);

CREATE TABLE ticket_qr (
    ticket_id  UUID PRIMARY KEY REFERENCES tickets(id),
    png        BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE show_sales (
    show_id       BIGINT PRIMARY KEY REFERENCES shows(id),
    tickets_sold  BIGINT NOT NULL DEFAULT 0,
    revenue_cents BIGINT NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
