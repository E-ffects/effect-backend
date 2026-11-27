ALTER TABLE users
    ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED'
        CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users SET is_admin = TRUE WHERE username = 'qa_good';
