CREATE TABLE users (
    id              UUID         PRIMARY KEY,
    username        VARCHAR(32)  NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Case-insensitive uniqueness for username (DOMAIN.md says unique, case-insensitive).
CREATE UNIQUE INDEX users_username_lower_unique ON users (LOWER(username));

CREATE UNIQUE INDEX users_email_unique ON users (email);
