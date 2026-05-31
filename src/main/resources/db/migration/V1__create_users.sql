CREATE TABLE users (
    id              UUID         PRIMARY KEY,
    username        VARCHAR(32)  NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Case-insensitive uniqueness for both username and email — emails are case-insensitive
-- by RFC convention on the domain part, and treating the local part case-insensitively
-- avoids near-duplicate accounts like alice@example.com vs Alice@example.com.
CREATE UNIQUE INDEX users_username_lower_unique ON users (LOWER(username));
CREATE UNIQUE INDEX users_email_lower_unique    ON users (LOWER(email));
