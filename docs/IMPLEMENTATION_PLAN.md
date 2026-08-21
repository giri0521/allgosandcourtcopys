# ALLGOSANDCOURTCOPYS — Final Implementation Plan

## Context

A government/court office needs a Document Management System to securely store, organize and
distribute official documents (Government Orders, Court Orders, Circulars, Contracts, Acts & Rules)
across 43 departments, for an internal user base of >100 people.

The repository today is **documentation only** — [README.md](README.md), [docs/](docs/) and an empty
`backend/` and `admin-web/`. There is no `package.json`, no build tooling, no source file of any kind.
Everything below is greenfield.

An earlier spec (`docs/ALLGOSANDCOURTCOPYS_Implementation_Plan_1.pdf`, deleted in commit `3c762b6`,
recoverable via `git show 72d9c18:docs/ALLGOSANDCOURTCOPYS_Implementation_Plan_1.pdf`) described a
mobile app with per-folder permission grants. **That model is superseded.** The PDF remains useful only
as the source of the wireframe screen inventory.

### Confirmed decisions

- **Website only.** One responsive React web app serving both roles. No mobile app.
- **Backend**: Java 21 + Spring Boot 3.x + PostgreSQL 16 (matches the existing `.gitignore`, which
  already ignores `backend/target/` and `backend/.mvn/`).
- **Storage**: code against the S3 SDK only. MinIO locally, AWS S3 or on-prem MinIO in production.
- **No profile pictures.** Initials only in the UI.
- **Login**: mobile number identity, **password credential**. OTP is not a way in and is not asked
  for at registration; the one that remains authorises a forgotten-password reset. SMS goes through a
  pluggable `OtpProvider` (mock in dev, MSG91/Twilio in prod), so DLT registration does not block work.
- **Permissions**: approval is the *only* gate. Once approved, everyone — member or admin — can view,
  download and **upload to any department**.
- **Deletion**: a member may delete a file **they uploaded**, and must supply a reason. The reason is
  pushed to every other active user as a notification. Admins may delete anything.

### Permission model

```
PENDING member   →  no access at all
APPROVED member  →  view + download + upload, ANY department
                    delete own uploads (reason required → notifies admins)
ADMIN            →  all of the above, plus: approve/reject registrations,
                    manage departments & folders, delete any file,
                    monitor all member activity, reports, audit logs
```

There is no per-folder permission table, no access-type flag, no per-department upload restriction and
no permission-matrix screen. **The only authorization questions in the system are: is this account
`ACTIVE`? is this route admin-only? and, for a delete, is this the uploader or an Admin?**

### Intended outcome

An Admin approves each self-registered user; once approved, that user works freely across the whole
document library, signing in with their mobile number and password, while every upload, download and
deletion is recorded and visible to Admins.

---

## Core requirement 1 — registration & approval

Both roles self-register. **No account is usable until an Admin approves it.** Enforced in the API
layer, never only in the UI.

```
    register  →  PENDING  ──approve──→  ACTIVE  ⇄  INACTIVE   (admin enable/disable)
                    │
                    └──reject──→  REJECTED   (with reason, terminal)
```

- Only `ACTIVE` accounts receive a usable JWT. `PENDING`, `REJECTED` and `INACTIVE` fail at login with a
  distinct message per state.
- Approval is a single action — there is nothing to configure at approval time.
- The **first Admin is seeded by migration**, not self-registered. Subsequent Admin registrations require
  approval by an existing Admin, so no one can grant themselves administrative access.

## Core requirement 2 — password login

Registration collects the password the user will sign in with, and the request goes straight to the
Admin queue — approval is the gate, so there is nothing a confirmation code would add.

```
POST /auth/login  { mobile, password }
        │
        ├─ status ≠ active            → 403 ACCOUNT_PENDING | _REJECTED | _INACTIVE
        ├─ locked_until > now         → 403 ACCOUNT_LOCKED
        ├─ BCrypt mismatch            → 401 INVALID_CREDENTIALS  (failed_login_count++)
        └─ otherwise                  → tokens
```

Five consecutive failures lock the account for fifteen minutes; the counter is committed in its own
transaction, so the rejection that follows cannot roll it back. One OTP remains — the
forgotten-password reset — and it issues no token of its own.

## Core requirement 3 — delete with reason

```
DELETE /api/v1/files/{id}   { reason }     reason required, min 10 chars
        │
        ├─ caller is the uploader, or an Admin   → soft delete (is_deleted = true)
        │       → write file_deletions row (file, actor, reason)
        │       → write audit_logs row
        │       → notification fan-out to EVERY active user but the deleter:
        │         "<name> deleted <file> from <department> — <reason>"
        │
        └─ otherwise → 403
```

Soft delete only: the row and the S3 object are retained so an Admin can restore. Admin deletions also
require a reason (recorded, but they are not notified about themselves).

---

## Architecture

```
admin-web  (React 18 + TS + Vite + Tailwind)         responsive; Admin + Member in one SPA
    │  HTTPS, JWT access (15 min) + refresh (7 d, httpOnly cookie)
    ▼
backend   (Spring Boot 3.x, Java 21, Maven)
    ├── Spring Security → JwtAuthFilter → @PreAuthorize
    ├── LoginPolicyService   ← the approval gate
    ├── Spring Data JPA + Flyway migrations
    ├── OtpProvider  (Mock | Msg91 | Twilio)   ← @ConditionalOnProperty
    └── StorageService (S3 SDK v2)  → MinIO (dev) / S3 (prod), presigned URLs
    ▼
PostgreSQL 16          S3-compatible object store
```

Repo layout:

```
backend/            Spring Boot, Maven, src/main/java/com/allgos/dms/...
admin-web/          the React app (keep the folder name; it serves both roles)
docs/
docker-compose.yml  postgres + minio for local dev
```

Backend packages, each with `controller / service / repository / entity / dto`:
`auth`, `user`, `department`, `folder`, `file`, `notification`, `audit`, `report`, `common`.

---

## Database schema

Flyway migrations in `backend/src/main/resources/db/migration/`. UUID PKs; `created_at` / `updated_at`
on every table.

| Table | Key columns |
|---|---|
| `users` | full_name, mobile_number **unique**, email (optional), password_hash, department_id, designation, role `admin\|member`, status `pending\|active\|rejected\|inactive`, failed_login_count, locked_until, token_version, last_login_at |
| `departments` | name **unique**, code, description, is_active — **seeded with the 43 departments** from `docs/Department list.txt` |
| `folders` | department_id, name, parent_folder_id (self-FK), category `contract\|govt_order\|court_order\|circular\|act_rule\|general`, file_count, created_by |
| `files` | folder_id, department_id (denormalised for filtering/reports), file_name, file_type, size_bytes, storage_key, checksum, uploaded_by, version, **is_deleted** |
| `file_deletions` | file_id, deleted_by, **reason** (text, not null), deleted_at, restored_by, restored_at |
| `registration_requests` | user_id, requested_role, status, reviewed_by, review_note, reviewed_at |
| `otp_verifications` | mobile_number, otp_hash, purpose `password_reset`, attempt_count, expires_at, verified_at |
| `downloads` | user_id, file_id, ip_address, created_at |
| `favorites` | user_id, file_id — unique pair |
| `notifications` | user_id, type, title, body, entity_ref, is_read |
| `audit_logs` | actor_id, action, entity_type, entity_id, metadata JSONB, ip_address, created_at |

No `member_folder_access`, no `photo_url`, no `download_permission` — removed by the decisions above.

**Approval happens once.** Editing a profile (name, email, designation) or changing a user's department
never sends the account back to `PENDING` and never triggers a second Admin review. The only way an
account loses access after approval is an Admin explicitly setting it to `INACTIVE`.

Indexes: `users(mobile_number)`, `users(status)`, `files(folder_id)`, `files(department_id)`,
`files(uploaded_by)`, `folders(department_id, parent_folder_id)`, `notifications(user_id, is_read)`,
`audit_logs(actor_id, created_at)`, and a GIN trigram index on `files(file_name)` for search.

The 43-department seed and the first-admin seed are separate migrations so admin details can be supplied
per environment.

---

## API surface

Base path `/api/v1`, common response envelope, `GlobalExceptionHandler`.

**Auth (public)**
- `POST /auth/register` — full name, mobile, department, designation, password, optional email →
  user `PENDING` + `registration_request`; nothing is sent
- `POST /auth/login` — mobile + password; issues tokens **only if** status = `active`
- `POST /auth/password/forgot` — rate-limited; sends a reset code
- `POST /auth/password/reset` — OTP-verified reset (no email link; mobile is the identity)
- `POST /auth/refresh`, `POST /auth/logout`, `POST /auth/logout-all` (bumps `token_version`)

**Any active user (member or admin)**
- `GET /departments`, `GET /departments/{id}/folders`, `GET /folders/{id}/files`
- `GET /files/{id}/preview` — short-lived presigned URL
- `GET /files/{id}/download` — writes a `downloads` row
- `POST /files` — multipart, multi-file, **any department**
- `DELETE /files/{id}` — body `{ reason }`; uploader or Admin only; notifies everyone but the deleter
- `PUT /files/{id}/replace` — uploader or Admin only; version bump
- `GET /search?q=` — global, with department/category/date facets
- `GET /me/uploads`, `GET /me/downloads`
- `GET|POST|DELETE /favorites`, `GET /notifications`, `PATCH /notifications/{id}/read`
- `GET /me`, `PATCH /me` (name, email, designation), `POST /me/password`

**Admin only** (`@PreAuthorize("hasRole('ADMIN')")`)
- `GET /admin/registration-requests?status=pending`
- `POST /admin/registration-requests/{id}/approve`, `POST /admin/registration-requests/{id}/reject`
- `GET /admin/members`, `PATCH /admin/members/{id}`, `PATCH /admin/members/{id}/status`
- `GET /admin/members/{id}/activity` — logins, uploads, downloads, deletions
- `POST|PATCH|DELETE /admin/departments`, `.../folders`
- `GET /admin/deletions`, `POST /admin/files/{id}/restore`
- `GET /admin/reports`, `GET /admin/audit-logs`, `GET /admin/stats`

---

## Screens

### Public
1. **Landing** — branding, Login, Register.
2. **Registration** — full name, mobile (+91, 10-digit validated), department dropdown, designation,
   password + confirm, optional email → **Pending Approval** screen.
3. **Login** — `+91` prefix, mobile number and password, "Forgot password?", "Secure Login" banner,
   "Don't have access? Contact your administrator".
4. **Forgot password** — mobile → OTP (6 boxes with auto-advance, **45-second resend countdown**) →
   new password.
5. **Access Restricted** — rendered on any 403, single "Go Back" action.

### All authenticated users
6. **Home** — notification bell with unread badge; welcome card; global search
   ("Search files, folders or departments…") with filters; stat tiles (Departments, Total files,
   My uploads, Downloads this month); Quick Access grid → Department List, Contract List, Court Orders,
   Circulars, Government Orders, Acts & Rules, Upload, Recent Files, Favorites, Downloads, Search,
   My Profile; Recent Files list.
7. **Department list → folders → folder contents** — all 43 departments, fully browsable.
8. **File preview** — PDF and image viewer; download card fallback for other types.
9. **File options** — Preview, Download, Share link, File Info; Delete and Replace shown only to the
   uploader and Admins.
10. **Upload** — pick **any** department, then folder; drag-and-drop or Browse; multi-file with per-file
    progress (Uploaded / Uploading % / Pending) and "Cancel All".
11. **Delete confirmation dialog** — names the file, warns that everyone is notified, and requires a
    reason (min 10 chars) before the button enables.
12. **My Uploads** — this user's contributions, with replace and delete.
13. **Downloads** — history, All / In Progress tabs.
14. **Search results** — global, with department and category facets.
15. **Notifications** — new file added, folder updated, and (for Admins) file-deleted-with-reason.
16. **My Profile** — name, role badge, mobile, email, department, designation, joined date, last login,
    account status. Editable: name, email, designation. Change password. Logout, Logout From All Devices.
17. **Side menu / nav** — profile header (initials), Home, Departments, Upload, My Uploads, Favorites,
    Search, My Profile, Help & Support, Settings, About, Privacy Policy, Logout (red), app version.

### Admin
18. **Dashboard** — "ADMIN PANEL", "System Administrator"; stat tiles Departments / Files / Downloads /
    Members / **Pending Requests**; Quick Actions: Add Department, Add Folder, Upload Files, Review
    Requests, Members, Deletions, Contract List, Court Orders, Circulars, Reports, System Logs;
    Departments carousel with file counts; **live member activity feed** (logins, uploads, downloads,
    deletions across everyone) — this is the monitoring surface.
19. **Registration Requests** — pending queue → detail → Approve or Reject with reason.
20. **Members** — All / Pending / Active / Inactive tabs; search; columns for department, designation,
    last login, uploads, downloads; actions enable / disable / edit / view activity.
21. **Member Activity detail** — one member's timeline, filterable by action and date.
22. **Deletions log** — every deleted file with its reason and who deleted it, plus **Restore**.
23. **Department management** — add / edit / deactivate; deleting a non-empty department is blocked.
24. **Folder management** — create / rename / move / delete, sub-folder tree.
25. **File management** — browse everything incl. deleted, replace, version, restore.
26. **Reports** — uploads and downloads per department, most-accessed files, most-active members,
    deletions, date range, CSV export.
27. **Activity / System logs** — `audit_logs` with actor, action, entity and date filters.
28. **Settings** — org details, OTP provider config, storage config, retention.
29. **Static pages** — Help & Support, About, Privacy Policy.

---

## Phases

Each phase ends with something demonstrable and merged to `main` behind a PR.

**Phase 0 — Foundations (week 1)**
Maven Spring Boot project in `backend/`; Vite + React + TS + Tailwind in `admin-web/`;
`docker-compose.yml` (Postgres + MinIO); Flyway wired; `.env.example`; GitHub Actions CI running
`mvn verify` and `npm run build` + lint; design tokens from the navy/blue branding in `docs/Logo.png`;
update [README.md](README.md) to drop the mobile app and the dead PDF link.
*Done when* `docker compose up` plus both dev servers gives a skeleton with `/actuator/health` reachable
from the web app.

**Phase 1 — Data model & auth (weeks 2–3)**
All Flyway migrations; entities and repositories; 43-department seed; first-admin seed; `OtpProvider` +
`MockOtpProvider` (logs the OTP) + `Msg91OtpProvider`; OTP send/verify with rate limiting; BCrypt
passwords; **`LoginPolicyService` implementing the approval gate**; JWT issue/refresh/revoke with
`token_version`; `GlobalExceptionHandler`; audit-log interceptor.
*Done when* an integration test proves: password login → tokens; a wrong password five times →
`ACCOUNT_LOCKED`; a forgotten password reset over OTP lets the new one in; and a `PENDING` account
gets nothing.

**Phase 2 — Registration & approval end-to-end (weeks 3–4)**
Registration, Pending Approval, Login and forgot-password screens; admin
request queue, approve, reject with reason, enable/disable; SMS + in-app notification on approval.
*Done when* a real person can register on staging and an Admin can approve them, every transition
audited. **Client demo milestone.**

**Phase 3 — Departments, folders, upload, delete (weeks 4–6)**
Department and folder CRUD with sub-folders; upload to any department with S3 multipart, per-file
progress, extension + magic-byte validation, size cap, virus-scan hook point; replace and versioning;
**delete-with-reason plus admin notification fan-out**; My Uploads; admin Deletions log with restore.
*Done when* a member uploads to a department that is not their own and it succeeds, then deletes their
own file with a reason and everyone else sees the notification — while deleting someone else's file 403s.

**Phase 4 — Browse, search, preview, download (weeks 6–8)**
Home dashboard with stat tiles and quick access, browsing across all 43 departments, PDF and image
preview via presigned URLs, download with progress and history, favorites, global search with facets,
notifications, Access Restricted screen.

**Phase 5 — Admin monitoring, reports, profile, static (week 9)**
Members list with tabs and per-member activity detail, dashboard activity feed, reports with CSV export,
audit-log viewer, My Profile and change password, settings, Help/About/Privacy.

**Phase 6 — Hardening & UAT (weeks 10–11)**
Security review, load test at 150 concurrent users, accessibility pass, responsive QA at 360 px /
768 px / 1440 px, cross-browser, bug fixing, client UAT.

**Phase 7 — Deploy & handover (week 12)**
Production environment, TLS, backups with a **tested restore**, monitoring and alerting, real SMS gateway
with DLT-approved templates, first-admin seeding, admin training, handover docs, warranty window.

---

## Security requirements

Non-negotiable; each gets a dedicated test:

- **Approval is the gate, enforced server-side.** A `PENDING`, `REJECTED` or `INACTIVE` account can never
  obtain a token by either login path. Never assume the client hides it.
- **Lockout is evaluated server-side.** The failure counter and `locked_until` live on the user row,
  and no request parameter can clear them.
- Admin-only endpoints are protected by `@PreAuthorize`, not by hiding menu items.
- Delete and replace re-check ownership on the server; a member cannot remove another member's file by
  guessing an id.
- Passwords: BCrypt, minimum length enforced server-side, throttled login attempts with lockout.
  Reset OTP: 6 digits, hashed at rest, 5-minute expiry, max 5 attempts, per-mobile and per-IP rate
  limits, single-use.
- JWT signed with a per-environment secret; refresh tokens revocable; `logout-all` bumps `token_version`.
- File access only via short-lived presigned URLs; the bucket is never public.
- Upload validation: extension **and** magic-byte MIME check, size cap, filename sanitisation.
- Every upload, preview, download, deletion, restore, approval, rejection and status change writes an
  `audit_logs` row. Deletions additionally write `file_deletions` with the reason.
- TLS in transit, encryption at rest, daily backups with restore drills, and written confirmation of data
  residency before any commercial cloud holds court documents.

---

## Testing

| Layer | Approach |
|---|---|
| Unit | JUnit 5 + Mockito on services (`LoginPolicyService` gets its own suite with a fixed `Clock`); Vitest on hooks and utils |
| Integration | Spring Boot Test + **Testcontainers** (Postgres + MinIO) for every controller |
| Authorization | `AuthorizationIT`: non-active statuses get no token; lockout after five failures; member hitting `/admin/**` 403s; member deleting another's file 403s; delete without a reason 400s |
| E2E | Playwright: register → approve → password login → browse → upload to another department → download → delete with reason → admin sees the notification. Runs in CI against docker-compose |
| Load | k6, 150 concurrent users, p95 < 500 ms on list endpoints |
| Manual | Responsive matrix + screen-by-screen checklist against the 29 screens above |

---

## Verification

1. `docker compose up -d` → Postgres and MinIO healthy.
2. `cd backend && ./mvnw verify` → green, including Testcontainers integration tests.
3. `cd admin-web && npm run build && npm run lint` → clean.
4. `./mvnw spring-boot:run` + `npm run dev`, then walk the happy path in a browser:
   - Register a member in "Agriculture" with a password → **Pending Approval**, nothing sent.
   - Confirm login is refused while `PENDING`.
   - Log in as the seeded Admin, approve the request.
   - Member logs in with their password → in.
   - Log out, get the password wrong five times → `ACCOUNT_LOCKED`.
   - Browse: all 43 departments visible. Preview and download a file.
   - Upload a PDF into a **Health** folder (not their department) → succeeds.
   - Delete that own upload with the reason "uploaded to the wrong folder" → gone from listings.
   - Back in the Admin account: the deletion notification is in the bell, the file is in the
     **Deletions log** with its reason, and **Restore** brings it back.
5. `curl` negative checks: member token → `DELETE /api/v1/files/{id}` for someone else's file → 403;
   `DELETE` without `reason` → 400; member token → any `/api/v1/admin/**` → 403.
6. **Forgot password** → the reset code appears in the backend log → the new password signs in and
   the old one does not.
7. `SELECT count(*) FROM departments;` → 43.

---

## Open items to confirm with the client

- Final hosting target (NIC/MeghRaj vs AWS) — affects Phase 7 only, not the code.
- MSG91 vs Twilio, and who owns the DLT registration — start it in week 1; it has a lead time.
- Timezone for the "once a day" boundary — plan assumes **Asia/Kolkata**, resetting at midnight IST.
- Whether a member may **replace** (new version of) their own file as well as delete it — plan assumes yes.
- Retention: how long soft-deleted files stay restorable before purge (plan assumes indefinite).
