-- Requires a coordinated cutover: old app versions cannot run against this schema.
-- Preserve Push subscriptions and their session association; never delete them on session expiry.
ALTER TABLE web_push_subscriptions
    ADD COLUMN session_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL;

UPDATE web_push_subscriptions subscription
JOIN auth_sessions session ON session.id = subscription.auth_session_id
SET subscription.session_hash = LOWER(HEX(session.session_id_hash));

ALTER TABLE web_push_subscriptions
    MODIFY COLUMN session_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    DROP FOREIGN KEY fk_web_push_subscriptions_auth_session,
    DROP INDEX ix_web_push_subscriptions_session_status,
    ADD INDEX ix_web_push_subscriptions_session_status (session_hash, status),
    DROP COLUMN auth_session_id;

DROP TABLE auth_sessions;
