# Phase 4 Handoff — Search, preview, favorites, notifications

Written 11 August 2026, handing over after Phase 3. Read [HANDOFF.md](HANDOFF.md) first — it is the
orientation and the four rules; this file is only what changed and what happens next.

---

## 1. Where things stand

Phase 3 is complete: **documents can be filed, read, deleted with a reason and restored.** The
system now does the thing it exists to do. A member signs in, browses any of the 43 departments,
uploads into any of them, and removes their own document with a reason that reaches every admin.

| | |
|---|---|
| Backend unit tests | **46 pass** (was 24) |
| Backend integration tests | **30 pass** (was 18) — including a real MinIO container |
| Web | typecheck, lint and build clean |
| Live smoke test | Upload, disguised-file refusal, presigned download, delete rules, restore — all verified against the dev stack |

The Phase 2 integration tests, which had never been executed, were run first and all passed. No
Phase 2 bugs were found.

### What landed

- `folder/` and `file/` packages: browsing, folder creation, upload, download, delete, restore
- `common/storage/`: `StorageService` over the AWS SDK, plus the presigner
- `UploadValidator`: extension **and** magic-byte checks, filename sanitisation, size cap
- Web: Departments, department folders, folder contents, upload dialog with per-file progress,
  My Uploads, and the admin Deletions log with restore

---

## 2. Decisions you are inheriting

These are deliberate. Changing one is fine, but do it knowingly.

**Bytes are written to storage before the database row.** Object storage cannot join the
transaction, so one of two failure modes had to be chosen. This way a committed row always has
bytes behind it; the residue is an orphaned object, which no user can see and a sweep can remove.
The reverse order would produce rows whose documents cannot be downloaded — visible, and much
worse. When the row fails to commit, `FileService` deletes the object it just wrote.

**`FileRecordWriter` is a separate bean, and returns a DTO rather than an entity.** Separate for the
`FailedAttemptRecorder` reason: `upload` has no transaction of its own, and calling a
`@Transactional` method on `this` would silently skip the proxy. Returning the view built *inside*
that transaction matters just as much — returning the entity meant the response serialiser walked a
lazy association with no session, which is exactly the 500 `FileAccessIT` caught.

**The declared content type decides nothing.** Tika reads the signature, and the extension, the
signature and the allow-list must all agree. A `.pdf` starting with `MZ` is refused. Filenames are
rebuilt from their last path segment, so `../../etc/passwd` becomes `passwd`.

**One bad file does not fail the batch.** `POST /files` returns 200 carrying `uploaded` and
`rejected`. Each file commits independently, so seven good documents survive the eighth being an
executable. The web app sends one request per file so each gets a real progress bar.

**Deleting is refused twice for a missing reason** — bean validation on the body, then again in the
service. The service check is the one that matters: the reason is the body of the notification every
admin receives, and an empty one would make the whole rule pointless.

**Who may delete is read from the stored row**, never inferred from the request. `canDelete` on the
wire is for hiding a button; the server re-decides on every call.

**Storage keys are opaque.** `{departmentId}/{folderId}/{uuid}{ext}`, with nothing from the
filename, so no one can guess another department's objects. The bucket is never public — every read
is a presigned URL valid for five minutes.

---

## 3. Phase 4 — the work

*Done when* a member finds a document by typing part of its name, previews it in the browser without
downloading, stars it, and sees the bell light up when an admin restores something of theirs.

Suggested order, each step leaving the build green:

### 3.1 Notifications (read side)

The rows have been written since Phase 1 and nothing has ever read them. This is the smallest piece
and unblocks the most visible gap.

- `GET /notifications?unread=`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`,
  `POST /notifications/read-all`. `NotificationService` already has `markRead` and `unreadCount`.
- A bell in `AppShell` with the count, and a notifications list screen (the route already exists).

### 3.2 Search

- `GET /files/search?q=&departmentId=&category=&from=&to=&page=`
- The schema already has **a GIN trigram index on `file_name`** — use `ILIKE '%…%'` or
  `similarity()` so the index is actually used; a `LOWER(...)` wrapper will not hit it.
- Scope it to `is_deleted = false`, and remember search spans all departments (rule 2).

### 3.3 Preview and download history

- Preview reuses `StorageService.presignedGet` with **no** `downloadFilename`, which is the
  difference between inline and attachment — that argument is already there for this.
- PDFs and images only; anything else offers a download instead.
- Write the `downloads` table on `GET /files/{id}/download-link` (the audit entry is written but
  the table is not), then `GET /downloads` for the history screen.

### 3.4 Favorites

- `favorites` already exists with `UNIQUE (user_id, file_id)`. `POST`/`DELETE /files/{id}/favorite`
  and `GET /favorites`. Treat re-favoriting as idempotent rather than a 409.

### 3.5 Home dashboard

Recent uploads, favorites, pending-request count for admins. Everything it needs now exists.

---

## 4. Already wired for you — do not rebuild it

| | |
|---|---|
| `downloads`, `favorites`, `notifications` tables | In `V1__baseline_schema.sql`, with their indexes |
| GIN trigram index on `file_name` | `idx_files_name_trgm` — the search index, already there |
| `NotificationService.markRead` / `unreadCount` | Written in Phase 1, never called |
| Presigned inline URLs | `StorageService.presignedGet(key, ttl, null)` — null means inline |
| `AuditAction.FILE_PREVIEWED` | Declared, unused |
| Paged responses, error shape, `AppShell` nav array | As before |
| `features/documents/api.ts` | Every document call on the web, in one place — add to it |

---

## 5. Conventions that will bite you

The full list is [HANDOFF.md §6](HANDOFF.md). The ones that matter most in Phase 4:

- **`AuditService.record` vs `recordDurable`.** `record` joins your transaction, for successful
  actions; `recordDurable` commits separately, for *rejected* attempts the exception would otherwise
  roll back.
- **Build response DTOs inside the transaction.** Every association on `StoredFile`, `Folder` and
  `FileDeletion` is lazy. A DTO assembled after the transaction closes throws
  `LazyInitializationException` and returns 500 — this cost time in Phase 3 twice, once in
  production code and once in a test assertion.
- **`*Test` is a unit test, `*IT` is a Testcontainers test.** Name it wrong and it never runs.
- **Server state on the web goes through TanStack Query, not `useEffect`** — the
  `react-hooks/set-state-in-effect` lint rule will reject a hand-rolled fetch hook. `UploadDialog`
  is the one deliberate exception, and says why in its comment: it drives progressive per-file state
  from upload callbacks, and still invalidates through the query client at the end.
- **Never edit an applied migration.** Phase 4 needs no schema change; if you find one missing, add
  `V4__…`.

---

## 6. Definition of done for Phase 4

- [ ] `./mvnw verify` green, including tests that prove search finds a file by a fragment of its
      name, a deleted file never appears in results, and a preview URL is inline rather than an
      attachment
- [ ] `npm run lint && npm run typecheck && npm run build` clean
- [ ] A member can search, preview, favorite and see their download history
- [ ] The bell shows unread notifications and marking one read sticks
- [ ] `docs/HANDOFF.md` §2 and §7 updated, and the next handoff written

---

## 7. Open questions — worth asking the client now

Carried forward from [HANDOFF.md §9](HANDOFF.md); the first two are the longest poles and neither
has moved.

- **SMS gateway:** MSG91 or Twilio, and who owns the India DLT registration? It blocks real OTP
  delivery in UAT and has a lead time. Still unstarted.
- **Hosting:** NIC/MeghRaj or AWS? Court documents on a commercial cloud need written sign-off.
- **File replacement:** may a member upload a new version of their own file? **Now blocking** —
  Phase 3 deliberately did not guess, so correcting a document today means deleting it with a reason
  and uploading again. `files.version` exists and is always 1, waiting for the answer.
- **Retention:** how long do soft-deleted files stay restorable before purge? Currently indefinite,
  and the bytes are never removed from storage.
- **Additional admins:** an existing admin promoting a member, or seeding only? Today it takes SQL.
