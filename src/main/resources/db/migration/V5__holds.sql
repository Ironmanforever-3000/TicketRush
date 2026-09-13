ALTER TABLE seats DROP COLUMN held_by;
ALTER TABLE seats ADD COLUMN held_by BIGINT;

CREATE TABLE holds (
    id BIGSERIAL PRIMARY KEY,
    show_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    seat_ids JSONB NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_holds_show FOREIGN KEY (show_id) REFERENCES shows(id),
    CONSTRAINT fk_holds_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_holds_show ON holds(show_id);
CREATE INDEX idx_holds_user ON holds(user_id);
CREATE INDEX idx_holds_expiry ON holds(expires_at);
