ALTER TABLE tickets ADD COLUMN IF NOT EXISTS qr_png BYTEA;

CREATE TABLE IF NOT EXISTS processed_messages (
    consumer_name VARCHAR(64) NOT NULL,
    event_id      BIGINT      NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE IF NOT EXISTS notification_log (
    event_id          BIGINT       NOT NULL,
    notification_type VARCHAR(32)  NOT NULL,
    recipient         VARCHAR(255) NOT NULL,
    sent_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, notification_type)
);

CREATE TABLE IF NOT EXISTS organizer_sales_summary (
    organizer_id        BIGINT PRIMARY KEY REFERENCES users(id),
    total_tickets_sold  BIGINT NOT NULL DEFAULT 0,
    total_revenue_cents BIGINT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
