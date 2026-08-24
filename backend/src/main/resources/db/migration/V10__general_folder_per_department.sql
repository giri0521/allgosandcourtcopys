-- Every department gets a "General" folder at its root, so a document with nowhere more specific
-- to go always has somewhere to land — the quick-upload flow files here whenever the uploader
-- does not choose a folder of their own.
--
-- WHERE NOT EXISTS rather than ON CONFLICT: the unique constraint on
-- (department_id, parent_folder_id, name) already stops a duplicate, but a department that already
-- has a folder called "General" (case-insensitively — an admin could have made one by hand before
-- this migration) should keep that one rather than fail this migration on the exact-name conflict.
INSERT INTO folders (department_id, name, category)
SELECT d.id, 'General', 'general'
FROM departments d
WHERE NOT EXISTS (
    SELECT 1 FROM folders f
    WHERE f.department_id = d.id
      AND f.parent_folder_id IS NULL
      AND lower(f.name) = 'general'
);
