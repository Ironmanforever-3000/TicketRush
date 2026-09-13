DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 1..200 LOOP
        INSERT INTO users (name, email)
        VALUES ('Test User ' || i, 'loadtest' || i || '@test.local')
        ON CONFLICT (email) DO NOTHING;
    END LOOP;
END $$;
