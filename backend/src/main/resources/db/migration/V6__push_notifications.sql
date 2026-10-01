-- Periodic push reminders ("remember to log your activities"), delivered over
-- Web Push with VAPID.
--
-- Two tables, deliberately separate:
--   * notification_preference holds WHAT the user asked for (one row per user,
--     created only when they first save a preference) plus the scheduler's own
--     bookkeeping column, last_sent_at;
--   * push_subscription holds WHERE to deliver it (one row per browser/device
--     that granted the permission), so the same preferences drive every device
--     the user signed in from.
--
-- Both cascade from user_auth: deleting an account - from the admin back office
-- or from the self-service screen - takes its reminders and its subscriptions
-- with it, exactly like every other table added since V1.

CREATE TABLE IF NOT EXISTS notification_preference (
    user_auth_user_id INT PRIMARY KEY,
    push_enabled      TINYINT(1) NOT NULL DEFAULT 0,
    -- Minutes between two reminders. The API accepts 15..1440 (a quarter of an
    -- hour to a day); the default matches the hourly cadence the screen offers.
    interval_minutes  INT NOT NULL DEFAULT 60,
    -- Hour of the day (0-23) the silent window opens and closes, evaluated in
    -- Europe/Rome. The window may wrap around midnight (22 -> 8 is the default:
    -- silent from 22:00 until 08:00). Both NULL = no silent window at all.
    quiet_hours_start TINYINT NULL DEFAULT 22,
    quiet_hours_end   TINYINT NULL DEFAULT 8,
    -- When the scheduler last sent a reminder to this user. NULL means "never",
    -- which makes the user due immediately; enabling the reminders therefore
    -- stamps it with the current time, so the first one arrives after a full
    -- interval instead of within the minute.
    last_sent_at      DATETIME NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                      ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_preference_user FOREIGN KEY (user_auth_user_id)
        REFERENCES user_auth (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS push_subscription (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    user_auth_user_id INT NOT NULL,
    -- URL of the browser's push service for this device. Unique across users,
    -- not per user: a browser may hand the same endpoint to a second account on
    -- the same machine, and in that case the row is reassigned rather than
    -- duplicated - two owners for one endpoint would deliver one user's
    -- reminders to the other's screen. 500 chars covers the FCM/Mozilla/WNS
    -- endpoints with room to spare; utf8mb4 x 500 = 2000 bytes stays inside the
    -- 3072-byte index limit of InnoDB's DYNAMIC row format.
    endpoint          VARCHAR(500) NOT NULL,
    -- The subscription's own public key and auth secret (base64url), used to
    -- encrypt the payload end-to-end: only this browser can read it.
    p256dh            VARCHAR(255) NOT NULL,
    auth              VARCHAR(255) NOT NULL,
    -- Purely informative, to let a user recognise a device in the UI.
    user_agent        VARCHAR(255) NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_push_subscription_endpoint UNIQUE (endpoint),
    CONSTRAINT fk_push_subscription_user FOREIGN KEY (user_auth_user_id)
        REFERENCES user_auth (user_id) ON DELETE CASCADE,
    -- "every subscription of this user", the query behind each reminder.
    INDEX idx_push_subscription_user (user_auth_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
