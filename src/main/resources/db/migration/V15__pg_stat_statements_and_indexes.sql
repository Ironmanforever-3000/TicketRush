-- Phase 7 Stage A: pg_stat_statements extension + cache-supporting indexes
-- pg_stat_statements requires shared_preload_libraries=pg_stat_statements in postgresql.conf
-- (added to docker-compose.yml postgres command)

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Index for the per-user hold-quota check:
--   SELECT count(*) FROM seats WHERE show_id = ? AND held_by = ? AND status = 'HELD'
-- Partial index: only rows with status='HELD' are indexed, keeping the index small.
CREATE INDEX IF NOT EXISTS idx_seats_user_quota
    ON seats(show_id, held_by, status)
    WHERE status = 'HELD';

-- Index for the per-user active-hold lookup:
--   SELECT * FROM holds WHERE user_id = ? AND status = 'ACTIVE' AND expires_at > now()
CREATE INDEX IF NOT EXISTS idx_holds_user_quota
    ON holds(user_id, status, expires_at)
    WHERE status = 'ACTIVE';
