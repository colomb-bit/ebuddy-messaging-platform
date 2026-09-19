-- eBuddy-style messaging platform schema
-- PostgreSQL 15+. Run inside a transaction through the migration tool.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE user_state AS ENUM ('active', 'suspended', 'deleted');
CREATE TYPE contact_state AS ENUM ('pending', 'accepted', 'blocked', 'removed');
CREATE TYPE message_state AS ENUM ('sent', 'delivered', 'read');
CREATE TYPE session_state AS ENUM ('active', 'revoked', 'expired');
CREATE TYPE outbox_state AS ENUM ('pending', 'processing', 'done', 'dead');

CREATE TABLE app_user (
    user_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username            VARCHAR(32) NOT NULL,
    username_norm       VARCHAR(32) NOT NULL,
    display_name        VARCHAR(80) NOT NULL,
    password_hash       VARCHAR(255),
    phone_e164          VARCHAR(20),
    state               user_state NOT NULL DEFAULT 'active',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at        TIMESTAMPTZ,
    CONSTRAINT app_user_username_ck CHECK (username ~ '^[A-Za-z0-9_.-]{3,32}$'),
    CONSTRAINT app_user_display_name_ck CHECK (length(btrim(display_name)) BETWEEN 1 AND 80)
);
CREATE UNIQUE INDEX app_user_username_uq ON app_user (username_norm);
CREATE UNIQUE INDEX app_user_phone_uq ON app_user (phone_e164) WHERE phone_e164 IS NOT NULL;

CREATE TABLE contact_list (
    owner_user_id       UUID NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    contact_user_id     UUID NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    state               contact_state NOT NULL DEFAULT 'pending',
    alias               VARCHAR(80),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (owner_user_id, contact_user_id),
    CONSTRAINT contact_not_self_ck CHECK (owner_user_id <> contact_user_id),
    CONSTRAINT contact_alias_ck CHECK (alias IS NULL OR length(btrim(alias)) BETWEEN 1 AND 80)
);
CREATE INDEX contact_list_owner_state_idx ON contact_list(owner_user_id, state, updated_at DESC);
CREATE INDEX contact_list_contact_idx ON contact_list(contact_user_id, state);

CREATE TABLE user_session (
    session_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    token_hash          BYTEA NOT NULL UNIQUE,
    client_type         VARCHAR(12) NOT NULL,
    device_label        VARCHAR(80),
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL,
    last_used_at        TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    state               session_state NOT NULL DEFAULT 'active',
    CONSTRAINT session_client_type_ck CHECK (client_type IN ('android', 'j2me', 'web', 'service')),
    CONSTRAINT session_expiry_ck CHECK (expires_at > issued_at),
    CONSTRAINT session_token_hash_ck CHECK (octet_length(token_hash) = 32),
    CONSTRAINT session_state_dates_ck CHECK (
        (state = 'active' AND revoked_at IS NULL) OR
        (state IN ('revoked', 'expired'))
    )
);
CREATE INDEX user_session_user_state_idx ON user_session(user_id, state, expires_at);
CREATE INDEX user_session_expiry_idx ON user_session(expires_at) WHERE state = 'active';

CREATE TABLE message (
    message_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_user_id         UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    recipient_user_id      UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    client_message_id      VARCHAR(64) NOT NULL,
    sequence_no             BIGINT GENERATED ALWAYS AS IDENTITY,
    body                    TEXT NOT NULL,
    status                  message_state NOT NULL DEFAULT 'sent',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at            TIMESTAMPTZ,
    read_at                 TIMESTAMPTZ,
    CONSTRAINT message_not_self_ck CHECK (sender_user_id <> recipient_user_id),
    CONSTRAINT message_client_id_ck CHECK (client_message_id ~ '^[A-Za-z0-9._:-]{1,64}$'),
    CONSTRAINT message_body_ck CHECK (length(body) BETWEEN 1 AND 4096),
    CONSTRAINT message_status_dates_ck CHECK (
        (status = 'sent' AND delivered_at IS NULL AND read_at IS NULL) OR
        (status = 'delivered' AND delivered_at IS NOT NULL AND read_at IS NULL) OR
        (status = 'read' AND delivered_at IS NOT NULL AND read_at IS NOT NULL)
    )
);
CREATE UNIQUE INDEX message_sender_client_uq ON message(sender_user_id, client_message_id);
CREATE UNIQUE INDEX message_sequence_uq ON message(sequence_no);
CREATE INDEX message_recipient_cursor_idx ON message(recipient_user_id, sequence_no);
CREATE INDEX message_sender_cursor_idx ON message(sender_user_id, sequence_no);
CREATE INDEX message_conversation_idx ON message(
    LEAST(sender_user_id, recipient_user_id), GREATEST(sender_user_id, recipient_user_id), sequence_no
);

-- Explicit receipts make acknowledgement idempotent and retain device-level evidence.
CREATE TABLE message_receipt (
    message_id          UUID NOT NULL REFERENCES message(message_id) ON DELETE CASCADE,
    recipient_user_id   UUID NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    delivered_at        TIMESTAMPTZ,
    read_at             TIMESTAMPTZ,
    PRIMARY KEY (message_id, recipient_user_id),
    CONSTRAINT receipt_dates_ck CHECK (read_at IS NULL OR delivered_at IS NOT NULL)
);

CREATE TABLE message_outbox (
    outbox_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id          UUID NOT NULL REFERENCES message(message_id) ON DELETE CASCADE,
    event_type          VARCHAR(32) NOT NULL,
    state               outbox_state NOT NULL DEFAULT 'pending',
    attempts            INTEGER NOT NULL DEFAULT 0,
    available_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at           TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    last_error_code     VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT outbox_attempts_ck CHECK (attempts >= 0),
    CONSTRAINT outbox_event_type_ck CHECK (event_type IN ('message.created', 'message.status'))
);
CREATE UNIQUE INDEX message_outbox_event_uq ON message_outbox(message_id, event_type);
CREATE INDEX message_outbox_work_idx ON message_outbox(state, available_at, outbox_id);

CREATE OR REPLACE FUNCTION enforce_message_status_transition()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status = 'delivered' AND OLD.status = 'read' THEN
        RAISE EXCEPTION USING ERRCODE = '22000', MESSAGE = 'invalid message status transition';
    END IF;
    IF NEW.status = 'read' AND OLD.status NOT IN ('delivered', 'read') THEN
        RAISE EXCEPTION USING ERRCODE = '22000', MESSAGE = 'invalid message status transition';
    END IF;
    IF OLD.status = 'read' AND NEW.status <> 'read' THEN
        RAISE EXCEPTION USING ERRCODE = '22000', MESSAGE = 'message status is monotonic';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER message_status_transition_trg
BEFORE UPDATE OF status ON message
FOR EACH ROW EXECUTE FUNCTION enforce_message_status_transition();

-- Recommended migration-time trigger for updated_at columns.
CREATE OR REPLACE FUNCTION touch_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;
CREATE TRIGGER app_user_touch_trg BEFORE UPDATE ON app_user FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER contact_list_touch_trg BEFORE UPDATE ON contact_list FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

COMMENT ON TABLE user_session IS 'Stores SHA-256 hashes of opaque bearer tokens, never raw tokens.';
COMMENT ON TABLE message_outbox IS 'Transactional outbox for post-commit WebSocket and legacy delivery fan-out.';
COMMENT ON COLUMN message.sequence_no IS 'Global monotonic cursor; clients use it to resume without offset pagination.';
