# Developer Handoff — ALLGOSANDCOURTCOPYS

A document management system for a government/court office: storing and distributing Government
Orders, Court Orders, Circulars, Contracts and Acts & Rules across 43 departments, for 100+ internal
users.

- **Repo** `github.com/harithkumaradhithya/allgosandcourtcopys` · **branch** `pre-release`
- **Full specification** [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) — this file is the
  orientation; that one is the contract.

---

## 1. The four rules

Every design decision follows from these. Changing one by accident is the easiest way to break the
product.

1. **Admin approval is the only access gate.** Members and admins both self-register. An account is
   `PENDING` and completely unusable until an admin approves it. There is no per-folder permission
   table, no access-type flag and no per-user download toggle.
2. **Once approved, everyone can do everything with documents.** Any active user can view, download
   and upload in *any* department. Uploads are deliberately **not** scoped to the user's own
   department — this replaced an earlier, stricter model.
3. **OTP once a day, password thereafter.** The first sign-in of each calendar day must be an OTP;
   afterwards password or OTP work until midnight (Asia/Kolkata). The date comes from the server
   clock, so no request can skip the OTP.
4. **Deleting your own file requires a reason**, which is sent to every admin as a notification.
   Admins can delete anything. Deletes are soft, so an admin can restore.

**All four are enforced server-side.** A hidden menu item never protects an endpoint.

---

## 2. What exists today

| Area | State | Notes |
|---|---|---|
| Database schema + seeds | Done | 3 Flyway migrations; 43 departments + first admin seeded |
| OTP auth & daily-OTP rule | Done | Verified live and by tests |
| JWT sessions, refresh, logout-all | Done | Access token 15 min; refresh in an httpOnly cookie |
| Audit trail | Done | register, OTP, login, failures |
| Login / register / pending screens | Done | Dual-tab login, 6-box OTP, 45s resend countdown |
| **Admin approval queue** | **Not built** | **Next task** — approving is currently a SQL update |
| Departments, folders, files, upload | Not built | Schema exists; no entities/endpoints/screens |
| Search, preview, download, favorites | Not built | Phase 4 |
| Reports, monitoring, audit viewer | Not built | Phase 5 |

10 unit tests and 7 integration tests pass. The integration tests run against a real PostgreSQL
container, so migrations and JPA entities are validated against each other on every build.

---

## 3. Getting it running

### Prerequisites

| Tool | Requirement |
|---|---|
| JDK 21 | `JAVA_HOME` must point at it |
| Node | **22.12+** — Vite 8 silently skips its native binary on older Node and the build fails |
| Maven | Not needed; use the committed `./mvnw` wrapper |
| Docker | Required for the database and the integration tests |

### Run

```bash
docker compose up -d                          # Postgres :5433, MinIO :9000 (console :9002)
cd backend && ./mvnw spring-boot:run          # http://localhost:8080
cd admin-web && npm install && npm run dev    # http://localhost:5173
```

### Checks before pushing

```bash
cd backend   && ./mvnw verify                 # unit + integration; needs Docker running
cd admin-web && npm run lint && npm run typecheck && npm run build
```

### Signing in

| Account | Mobile | How |
|---|---|---|
| System Administrator (seeded) | 9999999999 | OTP only — no password by design |
| Test member | 9123456780 | Password `Str0ngPassword!`, or OTP |

**Where the OTP appears:** no SMS is sent in development. `OTP_PROVIDER=mock` prints the code to the
backend log — look for `=== MOCK OTP ===`. Real SMS needs MSG91 or Twilio plus India DLT template
registration, which has a lead time and has not been started.

---

## 4. Environment traps already hit

All fixed in the repo; recorded so nobody re-diagnoses them.

| Symptom | Cause & resolution |
|---|---|
| `mvn` is not a command | The Maven download was the **source** distribution, which has no `bin/mvn`. Use `./mvnw`. |
| Integration tests fail with HTTP 400 from a working Docker | Docker Engine 29 refuses API versions below 1.40, which older Testcontainers negotiate. Pinned to Testcontainers 1.21.4 — do not downgrade. |
| App cannot authenticate to the database | A locally installed PostgreSQL owns 5432, so `localhost` resolves to it instead of the container. The container is published on **5433**. |
| MinIO console will not bind | Windows reserves port 9001. The console is published on **9002**. |

---

## 5. Codebase map

### Backend — `backend/`, Java 21 + Spring Boot 3.3

One package per feature area under `com.allgos.dms`, each with
`controller / service / repository / entity / dto`.

| File | Why it matters |
|---|---|
| `auth/service/LoginPolicyService` | The approval gate and the daily-OTP rule — the heart of the system |
| `auth/service/AuthService` | Register, OTP login, password login, refresh, logout |
| `auth/service/OtpService` | Code generation, hashing, expiry, attempt and send limits |
| `auth/service/FailedAttemptRecorder` | Brute-force counters that must survive rollback (§6) |
| `audit/service/AuditService` | Two record methods with different transaction semantics (§6) |
| `common/security/` | JWT issue/parse, auth filter, principal |
| `common/sms/OtpProvider` | Interface with mock and MSG91 drivers, chosen by config |
| `resources/db/migration/` | Flyway owns the schema; Hibernate is `validate` only |

### Web — `admin-web/`, React 19 + TypeScript + Vite + Tailwind 4

One responsive app serves both roles; there is no mobile app. `src/app/App.tsx` holds routes for every
planned screen, most still placeholders. Built screens are in `src/features/auth/`. `src/lib/api.ts`
centralises the 401 → login and 403 → Access Restricted conventions.

---

## 6. Conventions that will bite you

### Auditing: pick the right transaction

- `AuditService.record(...)` **joins your transaction.** Use it for successful actions, including any
  that reference a row you just created — an independent transaction cannot see that uncommitted row
  and the foreign key fails. This exact mistake made every registration return 500.
- `AuditService.recordDurable(...)` **commits separately.** Use it for *rejected* attempts, which
  would otherwise be rolled back by the very exception that caused them. The actor must already be
  committed.

### Counters that bound brute force must be durable

Incrementing an attempt counter and then throwing loses the increment to the rollback — which once
left OTP guessing effectively unlimited and password lockout unreachable. That is why
`FailedAttemptRecorder` is a **separate bean** with `REQUIRES_NEW` methods: calling such a method on
`this` bypasses the Spring proxy and silently loses the new transaction.

### Smaller conventions

- **Enum columns are lowercase** in the database (`admin`, `pending`) so SQL reads naturally. Java
  enums are uppercase, so every enum column uses an `AttributeConverter`, never `@Enumerated`, which
  would write values the CHECK constraints reject.
- **Never edit an applied migration.** Add a new versioned file.
- **Test naming decides what runs.** `*Test` is a fast unit test (Surefire); `*IT` is a Testcontainers
  integration test (Failsafe, during `verify`). Get it wrong and your test never runs.
- **Errors carry a machine code.** Every failure returns `{ code, message }`; the UI switches on
  `code` and displays `message`. Don't invent client-side wording for server-detected failures.
- **The access token lives in memory only**, never localStorage, so an XSS bug cannot read it.

---

## 7. Next steps, in order

### Phase 2 — Admin approval queue ← start here

The one thing blocking a real demo: an admin cannot approve anyone without SQL.

- `GET /admin/registration-requests?status=pending`, plus `approve` and `reject` (reject requires a
  reason)
- `GET /admin/members` and `PATCH /admin/members/{id}/status` for enable/disable
- Guard every route with `@PreAuthorize("hasRole('ADMIN')")`
- Notify the applicant on approve and reject — `NotificationService` already exists
- Web: pending queue, detail view, members list with All / Pending / Active / Inactive tabs
- Web: a route guard, so a member hitting an admin URL is bounced
- Tests: a member calling `/admin/**` must get 403

### Phase 3 — Departments, folders, upload, delete

Entities and endpoints for folders and files; S3 upload via MinIO with per-file progress; extension
**and** magic-byte validation; delete-with-reason plus the admin notification fan-out; deletions log
with restore.

### Phase 4 — Browse, search, preview, download

Home dashboard, browsing across all 43 departments, PDF and image preview via short-lived presigned
URLs, download with history, favorites, global search, notifications.

### Phase 5 — Admin monitoring, reports, profile

Per-member activity timelines, dashboard activity feed, reports with CSV export, audit log viewer,
My Profile, static pages.

### Phase 6 — Hardening and UAT

Security review, load test at 150 concurrent users, accessibility, responsive QA at 360/768/1440 px,
client UAT.

### Phase 7 — Deploy and handover

Production environment, TLS, backups with a tested restore, monitoring, the real SMS gateway with
DLT-approved templates, admin training.

---

## 8. Known gaps in what is already built

- **Refreshing the page signs you out.** The access token is deliberately in memory only, but the
  client never calls `/auth/refresh` on start-up to restore the session from the cookie.
- **No `GET /me` endpoint.** Planned, not written; session restore needs it or an equivalent.
- **Forgot-password screen missing**, though both backend endpoints already work.
- **Registration OTP step is not in the UI.** `/auth/otp/verify-registration` exists and registration
  sends a code, but the web app goes straight to Pending Approval.
- **Access Restricted screen is a placeholder**, though `api.ts` already redirects 403s to it.
- **CI has never run.** The workflow is committed but unproven.

---

## 9. Open questions for the client

- **SMS gateway:** MSG91 or Twilio, and who owns the DLT registration? It blocks real OTP delivery in
  UAT and has a lead time — worth starting now.
- **Hosting:** NIC/MeghRaj or AWS? Affects deployment only, but court documents on a commercial cloud
  needs written sign-off.
- **File replacement:** may a member upload a new version of their own file, or is that admin-only?
  Currently assumed yes.
- **Retention:** how long do soft-deleted files stay restorable before purge? Currently indefinite.
