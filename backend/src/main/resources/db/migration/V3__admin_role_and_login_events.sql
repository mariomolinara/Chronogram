-- Adds role-based access control and the login history needed by the admin
-- dashboard. Idempotent-friendly: run once by Flyway on an existing database.

-- Role of the account. Only two values are used today (USER, ADMIN); stored as a
-- string so adding a role later does not require renumbering.
ALTER TABLE user_auth
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- Marks the built-in administrator provisioned from configuration. A system
-- account must never be deleted or demoted; only its email and password change.
ALTER TABLE user_auth
    ADD COLUMN is_system TINYINT(1) NOT NULL DEFAULT 0;

-- Forces a password change on the next login. Set when the admin account is
-- first provisioned from ADMIN_INITIAL_PASSWORD, cleared once the password is
-- changed from the admin page.
ALTER TABLE user_auth
    ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0;

-- One row per successful login. `last_login` on user_auth only keeps the most
-- recent timestamp, which cannot answer "was this user active on each of the
-- last N days"; this table is the history behind the activity/regularity stats.
CREATE TABLE IF NOT EXISTS login_event (
    login_event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT       NOT NULL,
    login_at       TIMESTAMP NOT NULL,
    CONSTRAINT fk_login_event_user FOREIGN KEY (user_id)
        REFERENCES user_auth (user_id) ON DELETE CASCADE,
    -- Serves both "distinct users since X" and per-user day bucketing.
    INDEX idx_login_event_at (login_at),
    INDEX idx_login_event_user_at (user_id, login_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
