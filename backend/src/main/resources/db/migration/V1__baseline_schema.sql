-- ALLGOSANDCOURTCOPYS DMS — baseline schema
--
-- Authorization model (see docs/IMPLEMENTATION_PLAN.md):
--   Admin approval is the ONLY access gate. Once a user is ACTIVE they may view,
--   download and upload across every department. There is deliberately no
--   per-folder permission table and no per-user download flag.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ---------------------------------------------------------------- departments
CREATE TABLE departments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL UNIQUE,
    code        VARCHAR(32)  UNIQUE,
    description TEXT,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------- users
CREATE TABLE users (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name            VARCHAR(255) NOT NULL,
    mobile_number        VARCHAR(15)  NOT NULL UNIQUE,
    email                VARCHAR(255),
    password_hash        VARCHAR(255),
    department_id        UUID         REFERENCES departments (id),
    designation          VARCHAR(255),
    role                 VARCHAR(16)  NOT NULL DEFAULT 'member'
                             CHECK (role IN ('admin', 'member')),
    status               VARCHAR(16)  NOT NULL DEFAULT 'pending'
                             CHECK (status IN ('pending', 'active', 'rejected', 'inactive')),
    -- Daily-OTP rule: password login is refused until this equals today's server date.
    last_otp_login_date  DATE,
    token_version        INTEGER      NOT NULL DEFAULT 0,
    failed_login_count   INTEGER      NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,
    last_login_at        TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_status        ON users (status);
CREATE INDEX idx_users_department    ON users (department_id);

-- -------------------------------------------------------------------- folders
CREATE TABLE folders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id    UUID         NOT NULL REFERENCES departments (id),
    parent_folder_id UUID         REFERENCES folders (id),
    name             VARCHAR(255) NOT NULL,
    category         VARCHAR(32)  NOT NULL DEFAULT 'general'
                         CHECK (category IN ('contract', 'govt_order', 'court_order',
                                             'circular', 'act_rule', 'general')),
    file_count       INTEGER      NOT NULL DEFAULT 0,
    created_by       UUID         REFERENCES users (id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (department_id, parent_folder_id, name)
);

CREATE INDEX idx_folders_department ON folders (department_id, parent_folder_id);

-- ---------------------------------------------------------------------- files
CREATE TABLE files (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    folder_id     UUID         NOT NULL REFERENCES folders (id),
    -- denormalised from folders for cheap filtering and reporting
    department_id UUID         NOT NULL REFERENCES departments (id),
    file_name     VARCHAR(512) NOT NULL,
    file_type     VARCHAR(128) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    storage_key   VARCHAR(1024) NOT NULL,
    checksum      VARCHAR(128),
    uploaded_by   UUID         NOT NULL REFERENCES users (id),
    version       INTEGER      NOT NULL DEFAULT 1,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_files_folder      ON files (folder_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_files_department  ON files (department_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_files_uploader    ON files (uploaded_by);
CREATE INDEX idx_files_name_trgm   ON files USING GIN (file_name gin_trgm_ops);

-- ------------------------------------------------------------- file deletions
-- A member may delete a file they uploaded, but must give a reason; the reason
-- is fanned out to every admin as a notification.
CREATE TABLE file_deletions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id     UUID        NOT NULL REFERENCES files (id),
    deleted_by  UUID        NOT NULL REFERENCES users (id),
    reason      TEXT        NOT NULL,
    deleted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    restored_by UUID        REFERENCES users (id),
    restored_at TIMESTAMPTZ
);

CREATE INDEX idx_file_deletions_file ON file_deletions (file_id);
CREATE INDEX idx_file_deletions_at   ON file_deletions (deleted_at DESC);

-- ------------------------------------------------------ registration requests
CREATE TABLE registration_requests (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES users (id),
    requested_role VARCHAR(16) NOT NULL DEFAULT 'member',
    status         VARCHAR(16) NOT NULL DEFAULT 'pending'
                       CHECK (status IN ('pending', 'approved', 'rejected')),
    reviewed_by    UUID        REFERENCES users (id),
    review_note    TEXT,
    reviewed_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_registration_requests_status ON registration_requests (status, created_at DESC);

-- ----------------------------------------------------------------------- OTPs
CREATE TABLE otp_verifications (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mobile_number VARCHAR(15) NOT NULL,
    otp_hash      VARCHAR(255) NOT NULL,
    purpose       VARCHAR(24) NOT NULL
                      CHECK (purpose IN ('registration', 'login', 'password_reset')),
    attempt_count INTEGER     NOT NULL DEFAULT 0,
    expires_at    TIMESTAMPTZ NOT NULL,
    verified_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_otp_lookup ON otp_verifications (mobile_number, purpose, created_at DESC);

-- ------------------------------------------------------------------ downloads
CREATE TABLE downloads (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id),
    file_id    UUID        NOT NULL REFERENCES files (id),
    ip_address VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_downloads_user ON downloads (user_id, created_at DESC);
CREATE INDEX idx_downloads_file ON downloads (file_id);

-- ------------------------------------------------------------------ favorites
CREATE TABLE favorites (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id),
    file_id    UUID        NOT NULL REFERENCES files (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, file_id)
);

-- -------------------------------------------------------------- notifications
CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id),
    type       VARCHAR(48)  NOT NULL,
    title      VARCHAR(255) NOT NULL,
    body       TEXT,
    entity_ref VARCHAR(255),
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user ON notifications (user_id, is_read, created_at DESC);

-- ----------------------------------------------------------------- audit logs
CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID        REFERENCES users (id),
    action      VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64),
    entity_id   UUID,
    metadata    JSONB,
    ip_address  VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_actor  ON audit_logs (actor_id, created_at DESC);
CREATE INDEX idx_audit_action ON audit_logs (action, created_at DESC);
