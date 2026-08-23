-- Letters, and the templates they start from.
--
-- An office letter is mostly the same every time: the same From block, the same
-- standing wording, the same closing. What changes is who it goes to, its subject
-- and a few paragraphs. Templates hold the part that repeats; a letter holds the
-- part that does not, plus a copy of everything as it stood when it was written.

-- The From block needs a postal address, and nothing here had one.
ALTER TABLE users ADD COLUMN office_address VARCHAR(500);

CREATE TABLE letter_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(160) NOT NULL UNIQUE,
    description     VARCHAR(500),
    -- What the letter says before anybody types into it. Both optional: a template
    -- can be nothing but a name and a shape to fill in.
    default_subject VARCHAR(500),
    body            TEXT,
    salutation      VARCHAR(120),
    -- Kept rather than deleted once letters have been written from it, so the list a
    -- user chooses from can shrink without orphaning what came before.
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_by      UUID         REFERENCES users (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_letter_templates_active ON letter_templates (is_active, name);

CREATE TABLE letters (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id       UUID         NOT NULL REFERENCES users (id),
    -- ON DELETE SET NULL rather than cascade: retiring a template must never take
    -- the letters written from it with it.
    template_id     UUID         REFERENCES letter_templates (id) ON DELETE SET NULL,

    reference_no    VARCHAR(120),
    letter_date     DATE,

    -- Both blocks are stored as written, not derived on read. The From block is a
    -- snapshot of the author's details at the time: a letter reprinted next year has
    -- to show the address it was issued under, not the one whoever wrote it has now.
    from_block      TEXT         NOT NULL,
    to_block        TEXT         NOT NULL,

    salutation      VARCHAR(120),
    subject         TEXT         NOT NULL,
    reference       TEXT,
    body            TEXT         NOT NULL,
    enclosure       VARCHAR(500),
    copy_to         TEXT,
    sign_off        TEXT,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The only listing there is: a person's own letters, newest first.
CREATE INDEX idx_letters_author ON letters (author_id, created_at DESC);
