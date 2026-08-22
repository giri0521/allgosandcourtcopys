-- The office phonebook: two books in one table.
--
-- One holds the numbers for a department, the other the tahsildars and group members
-- of a taluk. They carry the same facts about a person — a name, what they do, a
-- number to ring — and differ only in what they hang from, so one table with a shape
-- constraint is honest where two tables would be the same columns written twice.
--
-- Nobody here needs an account. A phonebook whose entries had to be users would be
-- unable to hold the one number anybody actually looks up: the office down the road.

CREATE TABLE phonebook_contacts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind            VARCHAR(16)  NOT NULL
                        CHECK (kind IN ('department', 'taluk')),
    full_name       VARCHAR(255) NOT NULL,
    designation     VARCHAR(255),
    -- Taluk entries only. A tahsildar heads the taluk; group members are the rest of
    -- the office, and the distinction is the whole reason this book is asked for.
    role            VARCHAR(16)
                        CHECK (role IN ('tahsildar', 'group_member')),
    phone_number    VARCHAR(32)  NOT NULL,
    alternate_phone VARCHAR(32),
    email           VARCHAR(255),
    department_id   UUID         REFERENCES departments (id),
    taluk           VARCHAR(120),
    created_by      UUID         REFERENCES users (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- A row belongs to exactly one book and is filled in for that book. Enforced here
    -- rather than only in the service: a half-filled contact is invisible in both
    -- listings, which is the kind of row that is found years later by someone
    -- wondering why a number they entered never appeared.
    CONSTRAINT phonebook_contacts_shape CHECK (
        (kind = 'department' AND department_id IS NOT NULL AND taluk IS NULL AND role IS NULL)
     OR (kind = 'taluk'      AND taluk IS NOT NULL AND role IS NOT NULL AND department_id IS NULL)
    )
);

-- The two listings, each read in the order it is displayed.
CREATE INDEX idx_phonebook_department ON phonebook_contacts (department_id, full_name);
CREATE INDEX idx_phonebook_taluk ON phonebook_contacts (taluk, role, full_name);
