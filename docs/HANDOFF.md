# Developer Handoff — ALLGOSANDCOURTCOPYS

A document management system for a government/court office: storing and distributing Government
Orders, Court Orders, Circulars, Contracts and Acts & Rules across 43 departments, for 100+ internal
users.

- **Repo** `github.com/harithkumaradhithya/allgosandcourtcopys` · **branch** `pre-release`
- **Full specification** [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) — this file is the
  orientation; that one is the contract.
- **Picking up the next phase?** Start with [HANDOFF-PHASE-3.md](HANDOFF-PHASE-3.md) — what changed
  in Phase 2, what to verify first, and the Phase 3 work broken down.

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
| Registration OTP, forgot password, Access Restricted | Done | Registration is now two steps; reset is OTP-only |
| Session survives a page reload | Done | Start-up `/auth/refresh` exchanges the cookie for a token |
| Admin approval queue | Done | Approve, reject-with-reason, members list, enable/disable |
| **Departments, folders, files, upload** | **Not built** | **Next task** — Phase 3 |
| Search, preview, download, favorites | Not built | Phase 4 |
| Reports, monitoring, audit viewer | Not built | Phase 5 |

24 unit tests pass. 18 integration tests exist — the 7 original plus 11 in `AdminApprovalIT` — and
run against a real PostgreSQL container, so migrations and JPA entities are validated against each
other on every build. **The integration tests have not been run since the Phase 2 work landed**: the
machine that wrote it had no Docker. Run `./mvnw verify` on a machine that does before trusting
the build.

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
cd backend   && ./mvnw test                   # unit only, no Docker needed
cd admin-web && npm run lint && npm run typecheck && npm run build
```

### Signing in

| Account | Mobile | How |
|---|---|---|
| System Administrator (seeded) | 9999999999 | OTP only — no password by design |
| Test member | 9123456780 | Password `Str0ngPassword!`, or OTP |

**The demo path**, now that the queue exists: register a member → confirm the OTP → Pending Approval
→ sign in as the admin with an OTP → **Registration Requests** → Approve → the member signs in with
an OTP, then with their password for the rest of the day.

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
| Every Lombok getter reports "cannot find symbol" | The JDK is newer than 21. Lombok's annotation processor silently does nothing on JDK 24+, so the whole codebase looks broken. Build on **JDK 21**. |
| `JAVA_HOME is not defined correctly` while a JDK is installed | `JAVA_HOME` was pointing at the JDK's `bin` directory. It must point at the JDK **root**. |
| The IDE reports the same Lombok errors while `./mvnw` compiles cleanly | The language server is not running Lombok. Trust Maven, not the squiggles. |

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
| `admin/service/AdminUserService` | The approval gate from the admin side: approve, reject, enable/disable |
| `admin/controller/AdminUserController` | `@PreAuthorize` sits on the **class**, so a new endpoint cannot be left unguarded |
| `audit/service/AuditService` | Two record methods with different transaction semantics (§6) |
| `common/security/` | JWT issue/parse, auth filter, principal |
| `common/sms/OtpProvider` | Interface with mock and MSG91 drivers, chosen by config |
| `resources/db/migration/` | Flyway owns the schema; Hibernate is `validate` only |

### Web — `admin-web/`, React 19 + TypeScript + Vite + Tailwind 4

One responsive app serves both roles; there is no mobile app. `src/app/App.tsx` holds routes for every
planned screen, most still placeholders. Built screens are in `src/features/auth/` and
`src/features/admin/`. `src/lib/api.ts` centralises the 401 → login and 403 → Access Restricted
conventions.

| File | Why it matters |
|---|---|
| `lib/AuthProvider.tsx` | Holds the session; restores it from the refresh cookie on start-up |
| `lib/RouteGuards.tsx` | `RequireAuth` / `RequireAdmin`. A convenience — the server is the control |
| `features/admin/api.ts` | Every admin call, in one place |
| `components/layout/AppShell.tsx` | Header and navigation for signed-in screens |

**Server state goes through TanStack Query**, not `useEffect`. This is not a style preference: the
`react-hooks/set-state-in-effect` lint rule rejects a `setState` reachable from an effect body, so a
hand-rolled fetch-and-set-loading hook will not pass `npm run lint`. Follow the pattern in
`RegistrationRequestsPage` — `useQuery` to read, `useMutation` plus `invalidateQueries` to write, so
a list re-reads from the server after every decision rather than patching itself locally.

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

### Phase 2 — Admin approval queue · done

An admin can now review registrations in the UI, which was the last thing blocking a real demo.

| Endpoint | Notes |
|---|---|
| `GET /admin/registration-requests?status=pending\|approved\|rejected\|all` | Paged, newest first |
| `GET /admin/registration-requests/{id}` | |
| `POST /admin/registration-requests/{id}/approve` | 409 `REQUEST_ALREADY_REVIEWED` if reviewed |
| `POST /admin/registration-requests/{id}/reject` | `reason` is required |
| `GET /admin/members?status=&q=&page=` | Search matches name and mobile |
| `GET /admin/members/summary` | Counts for the tabs and the pending-requests tile |
| `PATCH /admin/members/{id}/status` | `ACTIVE` or `INACTIVE` only |

Three rules in that surface are worth knowing before changing it:

- **A pending account cannot be switched on from the members screen** (409
  `REGISTRATION_NOT_REVIEWED`). Activation goes through the queue, so every active account has a
  reviewed request behind it.
- **An admin cannot change their own status** (403 `CANNOT_MODIFY_SELF`) — the cheapest way to lock
  the last administrator out.
- **Approval never changes a role.** Promotion to admin stays a separate, deliberate act.

Also landed with it: session restore on reload, `RequireAuth`/`RequireAdmin` guards, the Access
Restricted screen, forgot-password, and the registration OTP step.

### Phase 3 — Departments, folders, upload, delete ← start here

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

- **The Phase 2 integration tests have never been executed** (§2). They compile; nothing more is
  known about them. This is the first thing to do on a machine with Docker.
- **No `GET /me` endpoint.** Session restore goes through `/auth/refresh`, which returns the user
  alongside the token, so nothing needs it yet — but anything wanting the current user without
  minting a token will.
- **The registration OTP cannot be resent.** `/auth/otp/send` issues a *login* code, which
  `/auth/otp/verify-registration` will not accept, so the screen offers "skip" instead of "resend".
  A registration-purpose resend endpoint would close it.
- **Notifications are written but never read.** Approval, rejection and disable all create rows;
  there is no bell, no list and no `GET /notifications`. Phase 4.
- **Admins are only created by seeding.** `registered_role` is always MEMBER and approval does not
  change a role, so a second admin currently needs SQL. The client has not been asked how they want
  admins promoted.
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
- **Additional admins:** how should a second administrator be created — an existing admin promoting a
  member, or seeding only? Approval deliberately never changes a role, so today it takes SQL.
