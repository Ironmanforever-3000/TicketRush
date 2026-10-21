-- Idempotency scope: change unique constraint to (user_id, idempotency_key)
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS uk_bookings_idempotency_key;
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS uk_bookings_user_id_idempotency_key;
ALTER TABLE bookings ADD CONSTRAINT uk_bookings_user_id_idempotency_key UNIQUE (user_id, idempotency_key);

-- Add CHECK that HELD implies held_by and hold_expires_at are set
ALTER TABLE seats DROP CONSTRAINT IF EXISTS chk_seats_held_state;
ALTER TABLE seats ADD CONSTRAINT chk_seats_held_state 
CHECK (
    (status = 'HELD' AND held_by IS NOT NULL AND hold_expires_at IS NOT NULL) 
    OR 
    (status != 'HELD')
);

-- Ensure a seat can appear in only one confirmed booking
-- By creating a unique index on the booking_seats table 
CREATE UNIQUE INDEX uq_booking_seats_seat_id 
ON booking_seats (seat_id);
