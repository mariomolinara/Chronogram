-- Baseline schema for Chronogram.
-- Reconstructed from the original JDBC DAO layer. Uses IF NOT EXISTS so it is
-- safe on a fresh database; on an existing DB Flyway is configured with
-- baseline-on-migrate=true, so this script becomes the version 1 baseline.

CREATE TABLE IF NOT EXISTS user_auth (
    user_id               INT AUTO_INCREMENT PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    created_at            TIMESTAMP NULL,
    updated_at            TIMESTAMP NULL,
    last_login            TIMESTAMP NULL,
    is_active             TINYINT(1) NOT NULL DEFAULT 1,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP NULL,
    CONSTRAINT uq_user_auth_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user (
    user_auth_user_id   INT PRIMARY KEY,
    name                VARCHAR(100),
    surname             VARCHAR(100),
    phone               VARCHAR(30),
    weekly_income       VARCHAR(45),
    weekly_income_other VARCHAR(45),
    weekly_home_cost    VARCHAR(45),
    notes               VARCHAR(255),
    gender              VARCHAR(20),
    birthday            DATE,
    address             VARCHAR(255),
    created_at          TIMESTAMP NULL,
    updated_at          TIMESTAMP NULL,
    CONSTRAINT fk_user_auth FOREIGN KEY (user_auth_user_id)
        REFERENCES user_auth (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS activity_type (
    activity_type_id INT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(45) NOT NULL,
    description      VARCHAR(255),
    is_instrumental  TINYINT(1) DEFAULT 0,
    is_routinary     TINYINT(1) DEFAULT 0,
    created_at       TIMESTAMP NULL,
    updated_at       TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS activity (
    activity_id      INT AUTO_INCREMENT PRIMARY KEY,
    activity_date    DATE NOT NULL,
    duration_mins    INT,
    pleasantness     INT,
    location         VARCHAR(100),
    cost_euro        DECIMAL(7,2),
    user_id          INT NOT NULL,
    activity_type_id INT NOT NULL,
    created_at       TIMESTAMP NULL,
    updated_at       TIMESTAMP NULL,
    CONSTRAINT fk_activity_user FOREIGN KEY (user_id)
        REFERENCES user_auth (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_type FOREIGN KEY (activity_type_id)
        REFERENCES activity_type (activity_type_id),
    INDEX idx_activity_user_date (user_id, activity_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    token_id        INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NOT NULL,
    selector        VARCHAR(64) NOT NULL,
    verifier_hash   VARCHAR(255) NOT NULL,
    expiration_time TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NULL,
    CONSTRAINT uq_prt_user UNIQUE (user_id),
    INDEX idx_prt_selector (selector),
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id)
        REFERENCES user_auth (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
