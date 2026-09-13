# Test A - Expired seat
docker exec ticketrush-postgres psql -U ticketrush -d ticketrush -c "UPDATE seats SET status = 'HELD', held_by = 7, hold_expires_at = now() - interval '1 minute' WHERE id = 5;"
# Test B - Future expiry
docker exec ticketrush-postgres psql -U ticketrush -d ticketrush -c "UPDATE seats SET status = 'HELD', held_by = 7, hold_expires_at = now() + interval '10 minutes' WHERE id = 6;"
# Test C - SOLD
docker exec ticketrush-postgres psql -U ticketrush -d ticketrush -c "UPDATE seats SET status = 'SOLD', held_by = NULL, hold_expires_at = now() - interval '10 minutes' WHERE id = 7;"
# Test D - Multiple expired
docker exec ticketrush-postgres psql -U ticketrush -d ticketrush -c "UPDATE seats SET status = 'HELD', held_by = 7, hold_expires_at = now() - interval '1 minute' WHERE id IN (8,9,10,11);"
