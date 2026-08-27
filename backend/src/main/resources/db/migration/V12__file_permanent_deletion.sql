-- Permanently deleting a document, on top of the existing soft delete: an admin may purge one
-- manually, or a daily sweep purges anything still deleted 30 days after it was removed (see
-- FileDeletion.PURGE_RETENTION).
--
-- Purging drops the `files` row and its bytes, so file_deletions.file_id must be allowed to become
-- null — the deletion log entry survives a purge (that is the point of it), so the file's identity
-- is captured onto this row at the moment it is deleted, rather than read through a join that a
-- purge would break for every reader of the log, not only the purged row.
ALTER TABLE file_deletions ADD COLUMN file_name       VARCHAR(512);
ALTER TABLE file_deletions ADD COLUMN file_type       VARCHAR(128);
ALTER TABLE file_deletions ADD COLUMN size_bytes      BIGINT;
-- Not foreign keys: a department or folder this pointed at may itself be deleted long after the
-- document was, and a snapshot of what it was called at the time should not be the thing standing
-- in the way of that later, the same way `files.folder_id` stood in the way of deleting an
-- apparently-empty folder before that was fixed.
ALTER TABLE file_deletions ADD COLUMN department_id   UUID;
ALTER TABLE file_deletions ADD COLUMN department_name VARCHAR(255);
ALTER TABLE file_deletions ADD COLUMN folder_id       UUID;
ALTER TABLE file_deletions ADD COLUMN folder_name     VARCHAR(255);
ALTER TABLE file_deletions ADD COLUMN purged_by       UUID REFERENCES users (id);
ALTER TABLE file_deletions ADD COLUMN purged_at       TIMESTAMPTZ;

-- Backfill every row already in the log from the file it still points at, so history recorded
-- before this migration keeps displaying correctly once nothing reads through that join anymore.
UPDATE file_deletions fd
SET file_name       = f.file_name,
    file_type       = f.file_type,
    size_bytes      = f.size_bytes,
    department_id   = f.department_id,
    department_name = d.name,
    folder_id       = f.folder_id,
    folder_name     = fo.name
FROM files f
JOIN departments d ON d.id = f.department_id
JOIN folders fo ON fo.id = f.folder_id
WHERE fd.file_id = f.id;

ALTER TABLE file_deletions ALTER COLUMN file_name       SET NOT NULL;
ALTER TABLE file_deletions ALTER COLUMN file_type       SET NOT NULL;
ALTER TABLE file_deletions ALTER COLUMN size_bytes      SET NOT NULL;
ALTER TABLE file_deletions ALTER COLUMN department_id   SET NOT NULL;
ALTER TABLE file_deletions ALTER COLUMN department_name SET NOT NULL;
ALTER TABLE file_deletions ALTER COLUMN folder_id       SET NOT NULL;
ALTER TABLE file_deletions ALTER COLUMN folder_name     SET NOT NULL;

-- file_id itself may now become null once its file is purged.
ALTER TABLE file_deletions DROP CONSTRAINT file_deletions_file_id_fkey;
ALTER TABLE file_deletions ALTER COLUMN file_id DROP NOT NULL;
ALTER TABLE file_deletions
    ADD CONSTRAINT file_deletions_file_id_fkey
    FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE SET NULL;

-- A purged file's stars and download history no longer point at anything real; cascading them away
-- is what lets the purge itself succeed, rather than the same kind of foreign-key violation that
-- made deleting an "empty" folder fail with no useful explanation.
ALTER TABLE favorites DROP CONSTRAINT favorites_file_id_fkey;
ALTER TABLE favorites
    ADD CONSTRAINT favorites_file_id_fkey
    FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE;

ALTER TABLE downloads DROP CONSTRAINT downloads_file_id_fkey;
ALTER TABLE downloads
    ADD CONSTRAINT downloads_file_id_fkey
    FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE;

-- What the daily sweep scans: still deleted, not yet purged, ordered by age.
CREATE INDEX idx_file_deletions_sweep ON file_deletions (deleted_at)
    WHERE restored_at IS NULL AND purged_at IS NULL;
