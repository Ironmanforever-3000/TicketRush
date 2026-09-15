ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN role VARCHAR(30);
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP;

-- Give existing users a default role and a mock BCrypt hash for "password123"
UPDATE users 
SET password_hash = '$2a$12$R.S6hE50rO3F8T9m2tP/R.h83w5C1t008l4J1v5m/9J/o4sY2C0qW', 
    role = 'CUSTOMER', 
    updated_at = CURRENT_TIMESTAMP 
WHERE role IS NULL;

ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL;
ALTER TABLE users ALTER COLUMN role SET NOT NULL;
ALTER TABLE users ALTER COLUMN updated_at SET NOT NULL;
