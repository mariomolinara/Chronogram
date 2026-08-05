-- Account approval workflow.
--
-- Registrations from a trusted email domain are auto-approved; everyone else
-- lands in PENDING and an administrator decides. `is_active` alone could not
-- express this: it cannot tell "never approved" from "blocked after approval",
-- and the two need different messages at login and different back-office
-- actions. `is_active` is kept as the derived "may authenticate" flag, so the
-- JWT filter and the password-reset flow are unaffected.

ALTER TABLE user_auth
    ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- Backfill from the existing flag: accounts already in use stay usable, and a
-- previously disabled account becomes BLOCKED rather than silently PENDING
-- (nobody ever requested approval for it).
UPDATE user_auth
SET account_status = CASE WHEN is_active = 1 THEN 'ACTIVE' ELSE 'BLOCKED' END;

-- The back office lists and counts accounts by state on every page load.
CREATE INDEX idx_user_auth_account_status ON user_auth (account_status);
