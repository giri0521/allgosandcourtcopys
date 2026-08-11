# Phase 3 Handoff — Departments, folders, upload, delete

> **Phase 3 is complete.** This file is kept as the record of what it was asked to do and why.
> Picking up the next phase? Go to [HANDOFF-PHASE-4.md](HANDOFF-PHASE-4.md).
>
> Two things below were answered by doing them, and the answers are worth carrying:
> §3's "run the integration tests first" was done — all 18 passed, no Phase 2 bugs. And §6's warning
> that storage is not transactional was settled by writing bytes before the row, so a committed row
> always has a document behind it.

Written 11 August 2026, handing over after Phase 2. Read [HANDOFF.md](HANDOFF.md) first — it is the
orientation and the four rules; this file is only what changed and what happens next.

---

## 1. Where things stand

Phase 2 is complete: **an admin can approve a registration in the UI**, which was the last thing
blocking the client demo. The full demo path works end to end — register → confirm OTP → pending →
admin approves → member signs in with an OTP, then with a password for the rest of the day.

| | |
|---|---|
| Backend unit tests | **24 pass** (was 10) |
| Backend integration tests | 18 exist — 7 original, 11 new — **not yet executed**, see §3 |
| Web | typecheck, lint and build clean |

### What landed

- `admin/` package: registration queue, approve, reject-with-reason, members list, enable/disable
- Registration Requests and Members screens, with an `AppShell` header and route guards
- Session now survives a page reload — start-up `/auth/refresh` exchanges the cookie for a token
- Registration OTP step, forgot-password, and the real Access Restricted screen

---

## 2. Decisions you are inheriting

These are deliberate. Changing one is fine, but do it knowingly.

**A pending account cannot be activated from the members screen.** `PATCH /admin/members/{id}/status`
returns 409 `REGISTRATION_NOT_REVIEWED` unless the account is already ACTIVE or INACTIVE. The effect
is that every active account has a reviewed registration request behind it, so the audit trail always
answers "who let this person in".

**An admin cannot change their own status** (403 `CANNOT_MODIFY_SELF`). Without it, the last
administrator can lock everyone out of the system with one click.

**Approving never changes a role.** `requested_role` is always MEMBER, and approval copies nothing.
A second admin therefore needs SQL today — see §8.

**Reviewing twice is refused** (409 `REQUEST_ALREADY_REVIEWED`) rather than silently repeated, so two
admins working the queue cannot have the second click overwrite the first one's decision.

**Enums are uppercase on the wire.** `ACTIVE`, `ADMIN`, `PENDING`. The database stores them lowercase
via `AttributeConverter`s, but nothing outside the backend sees that. The TypeScript types were
lowercase and wrong; they are now correct.

**Server state on the web goes through TanStack Query, not `useEffect`.** This is not taste: the
`react-hooks/set-state-in-effect` lint rule rejects a `setState` reachable from an effect body, so a
hand-rolled fetch-and-set-loading hook **will not pass `npm run lint`**. Copy the pattern in
`RegistrationRequestsPage` — `useQuery` to read, `useMutation` + `invalidateQueries` to write. Lists
re-read from the server after each decision instead of patching themselves locally, which is what
makes two admins working at once behave correctly.

---

## 3. Do this before writing any Phase 3 code

**Run the integration tests.** The machine that wrote Phase 2 had no Docker, so `AdminApprovalIT`
has never executed. It compiles; nothing more is known.

```bash
docker compose up -d
cd backend && ./mvnw verify
```

If something fails there it is Phase 2's bug, not yours — fix it before building on top.

### Environment traps that cost hours last time

| Symptom | Cause |
|---|---|
| Every Lombok getter is "cannot find symbol" | The JDK is newer than 21. Lombok's processor silently no-ops on JDK 24+, so the entire codebase looks broken. Build on **JDK 21**. |
| `JAVA_HOME is not defined correctly`, but a JDK is installed | `JAVA_HOME` was pointing at the JDK's `bin`. It must point at the JDK **root**. |
| The IDE shows those errors while `./mvnw` compiles cleanly | The language server is not running Lombok. Trust Maven. |

The rest are in [HANDOFF.md §4](HANDOFF.md).

---

## 4. Phase 3 — the work

*Done when* a member uploads to a department that is **not their own** and it succeeds, then deletes
their own file with a reason and every admin sees the notification — while deleting someone else's
file 403s.

Suggested order, each step leaving the build green:

### 4.1 Departments and folders (read)

- `Folder` entity + repository. The schema already exists: self-referencing `parent_folder_id`,
  a `category` CHECK constraint, and `UNIQUE (department_id, parent_folder_id, name)`.
- `GET /departments` (authenticated, richer than the public one used by the registration form),
  `GET /departments/{id}/folders`, `GET /folders/{id}/files`
- Category is an enum with a lowercase converter, like every other enum here — never `@Enumerated`.

### 4.2 Storage client

Nothing Java-side exists yet, but everything around it does (§5). Write a `common/storage/`
`StorageService` over the AWS SDK's `S3Client`:

- `put(key, stream, contentType, size)`, `presignedGet(key, ttl)`, `delete(key)`
- Key layout is your call, but make it opaque and non-guessable —
  `{departmentId}/{folderId}/{uuid}{ext}` rather than anything derived from the filename.
- **The bucket is never public.** Every read is a short-lived presigned URL.

### 4.3 Upload

- `POST /files` — multipart, multiple files, **any department**. Uploads are deliberately not scoped
  to the uploader's own department; that is rule 2 and it replaced an earlier, stricter model.
- Validate **extension and magic bytes**, not just the declared content type — a `.pdf` that begins
  with `MZ` must be refused. Plus a size cap and filename sanitisation.
- Leave a hook point where a virus scan will go.
- Write `files` (with `department_id` denormalised from the folder), bump `folders.file_count`, and
  audit `FILE_UPLOADED`.

### 4.4 Delete with a reason, and restore

This is the rule most likely to be implemented loosely, so be exact:

- `DELETE /files/{id}` takes `{ reason }`. **Missing or blank reason → 400.**
- The uploader or an admin may delete. Anyone else → **403**, re-checked on the server against the
  row, never inferred from what the client sent.
- Deletes are **soft**: set `is_deleted`, write a `file_deletions` row carrying the reason, and fan a
  notification out to every admin with that reason in the body —
  `NotificationService.notifyAllAdmins` already does the fan-out.
- `GET /admin/deletions` and `POST /admin/files/{id}/restore` for the admin log.

### 4.5 Web

Department list, folder tree, upload with per-file progress, My Uploads, and the admin Deletions log
with restore. `AppShell` gives you the header; add the new entries to its `NAV` array.

---

## 5. Already wired for you — do not rebuild it

| | |
|---|---|
| `files`, `folders`, `file_deletions`, `downloads`, `favorites` tables | All in `V1__baseline_schema.sql`, with indexes and a GIN trigram index on `file_name` for search |
| AWS SDK `s3` + `s3-transfer-manager` | In `pom.xml` |
| `app.storage.*` config | `application.yml` — endpoint, region, bucket, keys, `path-style-access: true` for MinIO |
| MinIO + bucket creation | `docker-compose.yml`, including a `minio-init` container that creates the bucket |
| Audit action names | `AuditAction` — `FILE_UPLOADED`, `FILE_DELETED`, `FILE_RESTORED`, `FOLDER_CREATED` and the rest are already declared |
| Notification types | `NotificationType.FILE_DELETED` / `FILE_RESTORED`, plus `notifyAllAdmins` |
| Paged responses | `common/dto/PageResponse` — use it, do not return Spring's `Page` |

---

## 6. Conventions that will bite you

The full list is [HANDOFF.md §6](HANDOFF.md). The ones that matter most in Phase 3:

- **`AuditService.record` vs `recordDurable`.** `record` joins your transaction — use it for
  successful actions, including any that reference a row you just created. `recordDurable` commits
  separately — use it for *rejected* attempts, which the exception would otherwise roll back.
  Getting this backwards made every registration return 500 once already.
- **Never edit an applied migration.** Phase 3 needs no schema change, but if you find one missing,
  add `V4__…`.
- **`*Test` is a unit test, `*IT` is a Testcontainers test.** Name it wrong and it never runs.
- **Every failure returns `{ code, message }`.** The UI switches on `code`; do not invent client-side
  wording for a server-detected failure.
- **Storage is not transactional.** A database rollback will not un-upload an object. Write the row
  first and upload after, or accept and sweep orphans — but decide deliberately rather than
  discovering it in UAT.

---

## 7. Definition of done for Phase 3

- [ ] `./mvnw verify` green, including a new `FileAccessIT` proving: member uploads to another
      department succeeds; delete without a reason 400s; deleting someone else's file 403s; the
      admin fan-out notification carries the reason
- [ ] `npm run lint && npm run typecheck && npm run build` clean
- [ ] A member can upload, see it, and delete it with a reason; every admin gets the notification
- [ ] An admin can restore it from the deletions log
- [ ] `docs/HANDOFF.md` §2 and §7 updated, and the next handoff written

---

## 8. Open questions — worth asking the client now

Carried forward from [HANDOFF.md §9](HANDOFF.md), plus one new:

- **SMS gateway:** MSG91 or Twilio, and who owns the India DLT registration? It blocks real OTP
  delivery in UAT and has a lead time. Still unstarted, and it is now the longest pole.
- **Hosting:** NIC/MeghRaj or AWS? Court documents on a commercial cloud need written sign-off.
- **File replacement:** may a member upload a new version of their own file, or is that admin-only?
  Currently assumed yes — Phase 3 implements it, so this needs an answer.
- **Retention:** how long do soft-deleted files stay restorable before purge? Currently indefinite.
- **Additional admins (new):** should an existing admin be able to promote a member, or is seeding
  the only route? Approval deliberately never changes a role, so today it takes SQL.
