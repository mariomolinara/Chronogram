-- Accesso con Google (OpenID Connect).
--
-- * auth_provider: come l'account e' stato creato. 'LOCAL' = email+password,
--   'GOOGLE' = ID token Google verificato dal backend. Un account LOCAL che
--   accede anche con Google resta LOCAL: il provider registra l'origine, il
--   collegamento a Google vive in google_subject.
-- * google_subject: claim `sub` dell'ID token, l'identificatore stabile
--   dell'utente Google (l'email puo' cambiare, il subject no). Unico ma
--   nullable: gli account solo-locali non lo hanno.
-- * password_hash diventa nullable: un account creato da Google non ha una
--   password locale finche' l'utente non ne imposta una via reset password.

ALTER TABLE user_auth MODIFY COLUMN password_hash VARCHAR(255) NULL;
ALTER TABLE user_auth ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE user_auth ADD COLUMN google_subject VARCHAR(255) NULL;

CREATE UNIQUE INDEX uq_user_auth_google_subject ON user_auth (google_subject);
