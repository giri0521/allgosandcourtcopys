-- The G.O. (Government Order) reference read from an uploaded PDF, e.g. "G.O.Ms.No.123".
--
-- Filled in server-side on upload, the same way as `description` — see DocumentAbstractExtractor.
-- Null wherever nothing could be read: a non-PDF, or a PDF with no G.O. reference on its first page.
-- Indexed the same way as file_name, since search matches this column too.
ALTER TABLE files ADD COLUMN go_number VARCHAR(100);
CREATE INDEX idx_files_go_number_trgm ON files USING GIN (go_number gin_trgm_ops);
