-- The abstract paragraph extracted from an uploaded PDF, shown as the document's description.
--
-- Filled in server-side on upload, from the document's own text (or OCR, for a scan) — never from
-- anything the client sends. Null wherever nothing could be read: a non-PDF, or a PDF that does not
-- follow the "ABSTRACT ... Issued." convention this office's government orders use.
ALTER TABLE files ADD COLUMN description TEXT;
