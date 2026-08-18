# Developer Handoff — ALLGOSANDCOURTCOPYS

A document management system for a government/court office: storing and distributing Government
Orders, Court Orders, Circulars, Contracts and Acts & Rules across 43 departments, for 100+ internal
users.

- **Repo** `github.com/harithkumaradhithya/allgosandcourtcopys` · **branch** `pre-release`
- **Full specification** [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) — this file is the
  orientation; that one is the contract.
- **How it fits together** [ARCHITECTURE.md](ARCHITECTURE.md) — where documents live, how a request
  flows, the security model, and what the client's IT will ask before signing off on hosting.
- **Picking up the next phase?** Start with [HANDOFF-PHASE-6.md](HANDOFF-PHASE-6.md) — what changed
  in Phase 5, what must be verified before anything else, and the Phase 6 work broken down.
  [HANDOFF-PHASE-3.md](HANDOFF-PHASE-3.md), [HANDOFF-PHASE-4.md](HANDOFF-PHASE-4.md) and
  [HANDOFF-PHASE-5.md](HANDOFF-PHASE-5.md) are kept as the record of what those phases were asked
  to do, and why each answered its questions the way it did.

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
| Departments, folders, browsing | Done | All 43 browsable by anyone signed in; folders nest |
| Upload, download, delete-with-reason, restore | Done | Magic-byte validation; presigned URLs; deletions log |
| File replacement | Done | Uploader or admin; keeps the id, bumps the version |
| Search, preview, favorites, notifications | Done | Trigram search; inline preview; the bell finally reads the rows |
| Download history | Done | Written when a presigned link is issued; a preview is not a download |
| My Profile, admin dashboard, audit viewer, reports | Done | CSV export; per-member activity; Help/About/Privacy |
| Hardening | Done | Auth rate limiting, security headers, production-readiness check, focus trap |
| **Client UAT** | **Outstanding** | The last thing before Phase 7 — see [HANDOFF-PHASE-6.md](HANDOFF-PHASE-6.md) |

**Every screen in the plan is built, and the whole system has now been verified against a running
stack.** What remains is client UAT and Phase 7 (deploy and handover).

**`./mvnw verify` is green: 135 unit and 70 integration tests**, against a real PostgreSQL container
and a real MinIO one. That first real run — three phases of code had never touched a database —
found four faults, all in the tests rather than in what they test; see the commit
"Fix four test-isolation faults found by the first real integration run".

Verified live against the running stack on 15 August 2026:

- **The four rules, end to end.** Pending account refused → approved → OTP sign-in → password for
  the rest of the day; upload into a department that is not the member's own; delete refused without
  a reason, then accepted with one, fanned out to every admin with the reason, and restored.
- **Upload validation.** An executable renamed `.pdf` was refused with `FILE_CONTENT_MISMATCH` while
  the genuine PDF in the same request was kept.
- **Presigned URLs.** The download link served the real bytes and they matched the original;
  preview carries no Content-Disposition, download does.
- **Search, favourites, download history** — including a favourite disappearing on delete and
  returning on restore, and history keeping its row marked unavailable.
- **CSV export** — byte-order mark present, 43 rows, and a department name *containing a comma*
  correctly quoted.
- **Security headers** on live responses; **member → `/admin/**` is 403**.
- **All 16 signed-in screens** driven in a browser against the live API. One real bug found and
  fixed: the Dashboard tab claimed to be active on every `/admin/*` screen.
- **Load test** at 15 VUs: 6,051 checks, 100% succeeded, list p95 234ms against a 500ms threshold.
  **Not the acceptance run** — see §8.

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

**None of the above tells you whether a screen is usable.** All three passed on a home page that had
no navigation and said the features were still to come, and on a department list where all 43 avatars
read "DO". Open the app and look at what you changed.

If you would rather not click through by hand, drive it — Playwright is already a dev dependency:

```bash
cd admin-web && npx playwright install chromium   # once per machine
```

```js
// admin-web/shot.mjs — throwaway; run with `node shot.mjs`, then look at the PNGs
import { chromium } from '@playwright/test';

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle' });
await page.getByLabel('Mobile number').fill('9123456780');
await page.getByLabel('Password').fill('Str0ngPassword!');   // needs an OTP sign-in earlier today
await page.getByRole('button', { name: 'Sign in' }).click();
await page.waitForURL('**/home');

await page.waitForLoadState('networkidle');
await page.waitForTimeout(800);        // entrance animations, or you shoot them at opacity 0
await page.screenshot({ path: 'home.png' });
await browser.close();
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
| `docker` exists but the daemon "cannot be found" | Docker Desktop is installed **per user** on this machine — `%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe`, not Program Files. Start it from there, then wait for `docker info` to answer. |

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
| `file/service/FileService` | A document's lifecycle: upload, replace, delete-with-reason, restore |
| `file/service/DiscoveryService` | Finding one: search, preview, favourites, history. Reads only — the lifecycle rules stay next door |
| `file/repository/StoredFileRepository` | The native search query, and why it must stay native (§7) |
| `user/service/ProfileService` | What a user may change about themselves — and what is simply not on the request |
| `admin/service/AdminReportService` | Stats, member activity, the audit viewer, the reports. Reads only |
| `admin/service/CsvWriter` | Excel's two traps: the byte-order mark, and cells that begin `=` |
| `common/security/AuthRateLimitFilter` | The per-address cap on `/auth/**`; runs before any password work |
| `common/config/ProductionReadinessCheck` | Refuses to start under `prod` with development defaults still set |
| `audit/service/AuditService` | Two record methods with different transaction semantics (§6) |
| `common/web/ClientIp` | One derivation of the caller's address, shared by the audit trail and the download history |
| `common/security/` | JWT issue/parse, auth filter, principal |
| `common/sms/OtpProvider` | Interface with mock and MSG91 drivers, chosen by config |
| `resources/db/migration/` | Flyway owns the schema; Hibernate is `validate` only |

### Web — `admin-web/`, React 19 + TypeScript + Vite + Tailwind 4

One responsive app serves both roles; there is no mobile app. `src/app/App.tsx` holds routes for every
planned screen. Built screens are in `src/features/auth/`, `src/features/admin/`,
`src/features/documents/`, `src/features/search/` and `src/features/notifications/`; the remainder —
reports, the audit viewer, profile, the static pages — are still `Placeholder` elements in that routes
file, which is the quickest way to see what is left. `src/lib/api.ts` centralises the 401 → login and
403 → Access Restricted conventions.

| File | Why it matters |
|---|---|
| `index.css` | The palette, the semantic surfaces **and** the motion vocabulary every screen borrows from |
| `lib/tones.ts` | What colour a category, file type or department is — asked, never chosen per screen |
| `components/ui/` | Button, Modal, Alert, Field, Skeleton, CategoryBadge, StatusBadge — polish lives here, so fixing it once fixes it everywhere |
| `components/layout/AppShell.tsx` | Header and navigation. **Every signed-in screen must use it** — a screen with its own layout gets no navigation, which is exactly how the home page once shipped unreachable |
| `lib/AuthProvider.tsx` | Holds the session; restores it from the refresh cookie on start-up |
| `lib/RouteGuards.tsx` | `RequireAuth` / `RequireAdmin`. A convenience — the server is the control |
| `features/admin/api.ts` | Every admin call, in one place |
| `features/documents/api.ts` | Every department, folder and file call, in one place — add to this rather than starting another |
| `features/documents/` | Departments, DepartmentPage, FolderPage, MyUploads, Favorites, Downloads, FilePreview, and the Upload / Replace / Delete / CreateFolder dialogs |
| `lib/queryKeys.ts` | `invalidateFileLists` — the one list of caches a document change invalidates. Add a new file-listing query key there and every existing mutation starts refreshing it |
| `components/layout/NotificationBell.tsx` | The unread badge, polled; failures are deliberately silent |
| `features/admin/audit/labels.ts` | Action name → words, and the metadata renderer. An unknown action still renders |
| `features/admin/reports/MonthlyActivityChart.tsx` | The only chart in the app. Read its header before changing the colours |
| `src/preview/chart-preview.tsx` | A dev-only harness — open `/chart-preview.html` on the dev server to see the chart against five awkward datasets without running the backend. Not in the production build |
| `src/preview/shell-preview.tsx` | The same trick for the application frame: `/shell-preview.html` renders the header as an admin |
| `src/preview/screens-preview.tsx` + `fixtures.ts` | **The whole application, driven by fixtures.** Swaps only axios's adapter, so the real router, guards and screens render. `node scripts/preview-screens.mjs` walks all 20 screens at a given width and flags overflow, console errors, placeholders and near-empty pages. Proves the screens draw; proves nothing about API wiring |

#### Look and feel is part of "done"

This is a client requirement, not a preference: **every screen, dialog and control is expected to
be visually finished, with motion, including the small things.** A screen that works but looks
unfinished is not finished. Budget for it in the estimate rather than leaving it as a follow-up
that never comes.

##### Colour

Navy is the brand, from the seal. **Gold is the secondary and is used sparingly** — a rule under the
logo, the hairline across the header, an accent edge. Never a gold button: gold cannot carry white
text accessibly, and a gold button beside a navy one starts an argument about which is the real
action.

| Where colour is decided | |
|---|---|
| `index.css` `@theme` | The navy and gold scales, plus semantic surfaces — `canvas`, `surface`, `surface-sunken`, `line`, `line-strong`. **Use `bg-surface` / `border-line`, not `bg-white` / `border-slate-200`**, so the whole application re-tones from six lines and a dark mode later has somewhere to land |
| `lib/tones.ts` | What each *thing* is coloured: `categoryTone`, `fileTypeTone`, `departmentTone`, and the nine-tone palette they draw from |

##### Type and elevation

- **Inter, self-hosted** via `@fontsource-variable/inter`, imported from `src/main.tsx`. It must be
  imported from the **JavaScript entry, not with `@import` in `index.css`** — Tailwind's PostCSS
  plugin inlines a CSS `@import` before Vite can rewrite the font URLs, so the `@font-face` rules
  ship, the `.woff2` files do not, and the page silently falls back to the system font while looking
  almost right. Self-hosted rather than a CDN because a government network may block one.
- **Three elevation steps**, in `@theme`: `shadow-card` for anything resting on the page,
  `shadow-lifted` for hover and for the sign-in card, `shadow-dialog` for modals. Each is two layers
  — a tight contact shadow plus a wider soft one — because a single-layer shadow is what makes an
  interface look like a slide deck. **Use these rather than Tailwind's `shadow-sm` / `shadow-md`.**
- **The canvas is deliberately quiet.** An earlier version stacked four gradients at ~30% each and
  tinted every white card blue by contrast. Two washes at 12–14% remain. If a screen needs more
  colour, it should come from the content, not the ground.
- **Large standalone numbers use proportional figures; columns use tabular.** `tabular-nums` is
  applied to table cells and the `.tabular-nums` class only — at display sizes it makes a number
  like `121` look loose.

`tones.ts` is the important one. A screen never picks a colour for a category or a department — it
asks. That is what stops a Court Order being indigo on one screen and amber on the next, and it
means **a screen built in a later phase inherits the scheme without its author knowing it exists**.
The category tones are assigned by meaning, not taste: court orders take the gravity of indigo,
government orders the navy of the seal, circulars a lighter sky, contracts the emerald of an
agreement in force, acts and rules the permanence of gold. General is grey on purpose — it is the
absence of a classification and should not compete with the five that mean something.

Departments get a stable tone derived from their id, so the one someone uses daily is findable by
colour rather than by reading, and it is the same colour on every screen for every user.

##### Motion

The vocabulary is defined once, in `src/index.css`, and everything borrows from it:

| | |
|---|---|
| `--ease-settle` | The single easing curve. It settles rather than bounces — this is a records system, not a game |
| `--duration-quick / -base / -slow` | 120ms for a press, 220ms for anything the eye follows, 400ms for content arriving |
| `animate-rise` / `animate-fade` / `animate-pop` | Content arriving, backdrops, dialogs |
| `stagger` | Put it on a list container and children arrive in sequence, capped at eight |
| `skeleton` + `SkeletonCards` / `SkeletonRows` | **Loading states are skeletons, never the word "Loading"** — a skeleton holds the layout still so arriving content lands where the eye already is |

Four rules that keep it coherent:

- **Reuse the vocabulary; do not invent per-screen animation.** Motion that varies screen to screen
  reads as sloppiness rather than personality.
- **Never remove the `prefers-reduced-motion` block** at the foot of `index.css`. Some people are
  made ill by animation; that block is what makes animating freely everywhere else safe.
- **Colour is never the only signal.** Every status carries a shape, an icon or a word as well —
  see `StatusMark` in `UploadDialog` and `FileGlyph` in `FileTable`.
- **Hover-only controls must also appear on focus.** Touch and keyboard users have no hover; the
  row actions in `FileTable` show the pattern (`focus-within`, and full opacity below `sm`).

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

### A derived repository method fails at startup, not at compile time

`findByUserIdAndFileDeletedFalse` is parsed from its own name when the context starts. Misspell a
property and the code compiles perfectly, then fails to boot — or fails only inside an integration
test, which needs Docker to run at all. On a machine without Docker the mistake is invisible.

`RepositoryQueryDerivationTest` parses every derived finder against its entity using Spring Data's
own `PartTree`, with no database, in the ordinary unit suite. **Add a row to it whenever you add a
derived finder.** Methods carrying an explicit `@Query` are not derived and do not belong there.

### Cleaning up test data: foreign keys have an order

Integration tests clear tables explicitly, because audit and notification rows are written in their
own transactions and survive a rollback. Everything pointing at a file — `downloads`, `favorites`,
`file_deletions` — must be deleted before the files themselves. Miss one and the failure surfaces in
the *next* test's setup, which is a miserable thing to debug.

### Smaller conventions

- **Enum columns are lowercase** in the database (`admin`, `pending`) so SQL reads naturally. Java
  enums are uppercase, so every enum column uses an `AttributeConverter`, never `@Enumerated`, which
  would write values the CHECK constraints reject.
- **Never edit an applied migration.** Add a new versioned file.
- **Test naming decides what runs.** `*Test` is a fast unit test (Surefire); `*IT` is a Testcontainers
  integration test (Failsafe, during `verify`). Get it wrong and your test never runs.
- **A `*IT` needing object storage extends `AbstractStorageIntegrationTest`**, which owns one MinIO
  container for the whole JVM. A second container per class costs tens of seconds for no confidence.
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

### Phase 3 — Departments, folders, upload, delete · done

Documents can be filed, read, removed and put back.

| Endpoint | Notes |
|---|---|
| `GET /departments` | Authenticated; richer than the public one, with folder and file counts |
| `GET /departments/{id}/folders?parentFolderId=` | Omit the parameter for the department's top level |
| `POST /departments/{id}/folders` | 409 `FOLDER_EXISTS` for a duplicate name in the same place |
| `GET /folders/{id}`, `/folders/{id}/breadcrumb`, `/folders/{id}/folders`, `/folders/{id}/files` | Breadcrumb is root-first |
| `POST /files?folderId=` | Multipart, multiple files, **any department** |
| `POST /files/{id}/replace` | Single part named `file`; keeps the id, bumps `version` |
| `GET /files/{id}`, `/files/my-uploads` | |
| `GET /files/{id}/download-link` | A presigned URL valid for 5 minutes |
| `DELETE /files/{id}` | Body `{ reason }`; blank → 400, not yours → 403 `NOT_FILE_OWNER` |
| `GET /admin/deletions?status=deleted\|all` | The deletions log, with the reason |
| `POST /admin/files/{id}/restore` | 409 `FILE_NOT_DELETED` if it was never deleted |

Four decisions in that surface are worth knowing before changing it:

- **Uploads are not scoped to the uploader's department.** There is deliberately no check comparing
  the two — see rule 2. Adding one would be a regression, not a fix.
- **The declared content type decides nothing.** The extension, the signature Tika reads from the
  bytes, and the configured allow-list must agree, so a `.pdf` beginning with `MZ` is refused with
  `FILE_CONTENT_MISMATCH`. Filenames are rebuilt from their last path segment.
- **A rejected file does not fail the batch.** `POST /files` returns 200 with `uploaded` and
  `rejected` lists; only an empty selection fails outright. The web app uploads one file per
  request so it can draw a real progress bar for each.
- **Bytes are written before the row.** Object storage does not join the transaction, so a
  committed row always has bytes behind it and the residue is an orphaned object rather than a row
  pointing at nothing. `FileRecordWriter` is a separate bean for the same proxy reason as
  `FailedAttemptRecorder`, and it returns the response view built inside its own transaction.

### Phase 4 — Search, preview, favorites, notifications · done

A document can now be found, looked at, kept and revisited — and the notifications the system has
been writing since Phase 1 are finally readable.

| Endpoint | Notes |
|---|---|
| `GET /files/search?q=&departmentId=&category=&from=&to=` | Minimum two characters → 400 `SEARCH_TOO_SHORT` |
| `GET /files/recent` | Newest across every department, for the dashboard |
| `GET /files/{id}/preview-link` | **Inline** presigned URL; 400 `PREVIEW_UNSUPPORTED` for anything else |
| `POST\|DELETE /files/{id}/favorite`, `GET /favorites` | Both directions idempotent |
| `GET /downloads` | The caller's own history |
| `GET /notifications?unread=`, `/notifications/unread-count` | Scoped to the caller by id |
| `POST /notifications/{id}/read`, `/notifications/read-all` | Someone else's id → 404, not 403 |

Five decisions worth knowing before changing it:

- **Search is a native query on purpose.** The schema carries a GIN trigram index on `file_name`, and
  `ILIKE '%…%'` is what uses it. The JPQL equivalent indexes `lower(file_name)` — a different
  expression from the one the index was built on — so rewriting it in JPQL would pass its tests on a
  handful of rows and quietly stop scaling. Every optional facet is wrapped in a `cast(… as …)`
  because PostgreSQL cannot infer the type of a null parameter in a native statement.
- **LIKE wildcards in a query are escaped.** Searching for `%` looks for a percent sign rather than
  matching the whole archive.
- **Preview and download differ by one argument.** `StorageService.presignedGet(key, ttl, filename)`
  attaches a Content-Disposition when given a filename and does not when passed null. That is the
  entire difference between rendering in the page and saving to disk.
- **A preview is not a download.** Previewing is audited but writes no `downloads` row. The history
  answers "who has a copy of this", and counting glances would make it meaningless.
- **`favorite` and `previewable` ride on every `FileView`.** Stars are resolved with one query per
  page, not one per row — see `DiscoveryService.viewsOf`.

### Phase 5 — Admin monitoring, reports, profile · done

The office can now answer "what has this person been doing", "what happened to this document" and
"how much is this system being used" without opening a database client.

| Endpoint | Notes |
|---|---|
| `GET\|PATCH /me`, `POST /me/password` | Name, email and designation only; the password change ends every session |
| `GET /admin/stats` | The dashboard tiles |
| `GET /admin/activity` | The dashboard feed — the whole trail, newest first |
| `GET /admin/members/{id}/activity` | Summary counters plus that member's paged timeline |
| `GET /admin/audit-logs?actorId=&action=&from=&to=` | Filtered by Specification, not sixteen query variants |
| `GET /admin/audit-logs/actions` | The filter's vocabulary, read from `AuditAction` by reflection |
| `GET /admin/reports?from=&to=` | Departments, top uploaders, twelve-month series |
| `GET /admin/reports/export?type=` | `departments` · `uploaders` · `monthly`, streamed as CSV |

Five decisions worth knowing before changing it:

- **A profile edit cannot reach a role, a status or a department.** Those fields are not on the
  request record, so there is no payload that changes them — not merely a check that could be
  removed. Approval happens once; editing a profile never returns an account to PENDING.
- **Changing a password bumps `token_version`**, ending every session including the one making the
  request. If the reason for the change was that someone else knew the old password, leaving their
  session alive would defeat it. The daily-OTP stamp is deliberately *not* cleared: it records that
  this person proved possession of their phone today, which a password change neither confirms nor
  invalidates.
- **The audit trail is read-only to the application.** There is no delete on the repository and no
  endpoint that edits an entry, by design.
- **Report periods are half-open and resolved in Asia/Kolkata.** `to` is widened to the start of the
  next day, so "to 3 March" includes a document filed at 4pm on the 3rd. Anything else either loses
  the last day or counts a boundary document in two adjacent reports.
- **Every department appears in the report, including the quiet ones.** A report that omitted them
  would make "no activity" indistinguishable from "not asked about".

### Phase 6 — Hardening and UAT · partly done ← finish here

The code half is done. The half that needs a running stack is not, and cannot be until someone runs
this on a machine with Docker.

**Done:**

| | |
|---|---|
| Per-address rate limit on `/auth/**` | 60/min, before any BCrypt work. The per-account limits could not stop a script spreading attempts across many accounts |
| Security headers | HSTS, CSP, Referrer-Policy, Permissions-Policy — asserted by `SecurityHeadersIT` so a chain refactor cannot drop them |
| `ProductionReadinessCheck` | Under `prod`, refuses to start on a placeholder JWT secret, MinIO's default credentials, a localhost CORS origin, a disabled throttle or the mock OTP provider |
| Lombok 1.18.46 | Closes the JDK 24+ trap that cost time in three phases; verified compiling on both 21 and 25 |
| Dependency audit | `npm audit` clean. Maven: see §8 — Spring Boot 4.1 is available and deliberately not taken |
| Accessibility | Both palettes validated with data, not judgement; modal focus trap added |
| Responsive | Search reachable below `md`; header rendered at 360/768/1440 with no horizontal overflow |
| Load test | `load/browse.js` — written, thresholds asserted, **never run** |

**Outstanding:** the load run, the click-through, and client UAT.
[HANDOFF-PHASE-6.md](HANDOFF-PHASE-6.md) has the ordered list.

Three decisions worth knowing:

- **The rate limit trusts `X-Forwarded-For`.** Behind a proxy that does not overwrite that header an
  attacker can rotate it and evade the limit; behind one that does not *set* it, every user collapses
  onto one address and the whole office is throttled together. **The reverse proxy must set it.**
- **The limit is per instance, not per cluster.** Two instances behind a balancer each enforce their
  own count. Fine for one instance and ~150 users; a second instance needs Redis or the proxy's own
  limiter.
- **`ProductionReadinessCheck` fails start-up rather than warning.** A warning in a log nobody reads
  is the same as no check. An instance that refuses to start gets fixed in minutes.

### Phase 7 — Deploy and handover

Production environment, TLS, backups with a tested restore, monitoring, the real SMS gateway with
DLT-approved templates, admin training.

---

## 8. Known gaps in what is already built

- **The load test has not been run at the acceptance figure.** It passed at 15 concurrent users with
  every threshold met, which validates the script and the endpoints — but the plan says **150**, and
  the machine available (7.4 GB, already running Docker, PostgreSQL, MinIO and the application) had
  nothing left to generate load with. Worse, **the database held one document**, so those timings
  describe framework overhead rather than how the queries behave at volume. Re-run at 150 against a
  realistic corpus, and check the search query plan rather than trusting the timing — a sequential
  scan is fast on one row and catastrophic on fifty thousand.
- **UAT has not happened.** Everything below the client is verified; the client has not seen it.
- **Spring Boot 3.3.4 is a year behind; 4.1.0 is available.** Deliberately not taken: a major
  upgrade with 39 integration tests that have never run is not a hardening change, it is a gamble.
  Do it once the suite is green, on its own branch. The same applies to Tika (2.9 → 4.0 beta) and
  the AWS SDK. Lombok was the one exception, because it fixes a trap that was actively costing time
  and is verified by compiling on two JDKs.
- **No CVE scan has run.** `npm audit` is clean, but OWASP dependency-check needs an NVD API key and
  there is none configured. Get a key and add it to CI — that is the check that would actually catch
  a vulnerable transitive dependency.
- **Superseded versions are kept but unreachable.** Replacing a document writes the new bytes to a
  new key and leaves the old object in place, with its key recorded in the `file_replaced` audit
  entry. Nothing in the application reads it, so recovering a mistaken replacement means an admin
  fetching that key by hand. A `file_versions` table would make it a screen; the client has not
  asked for version history.
- **Orphaned objects are never swept.** If the process dies between storing bytes and committing
  the row, the object stays — as do superseded versions, above. Nothing references either and no
  user can see them, but a periodic sweep comparing keys against `files.storage_key` is still owed,
  and it must not treat a key named in a `file_replaced` audit entry as an orphan.
- **The audit trail has no retention policy.** Every sign-in, preview and download writes a row, and
  nothing ever removes one. That is correct for evidence and untenable forever: at 150 users the
  table will be the largest in the database within a year. The client has not been asked how long a
  trail must be kept, and until they are, nothing should be deleted.
- **Reports are computed on demand.** Every load runs half a dozen aggregates over `files` and
  `downloads`. Fine at this size, and the indexes are there; if a year of data makes the screen slow,
  a nightly rollup table is the answer rather than a cache.
- **The registration OTP cannot be resent.** `/auth/otp/send` issues a *login* code, which
  `/auth/otp/verify-registration` will not accept, so the screen offers "skip" instead of "resend".
  A registration-purpose resend endpoint would close it.
- **The bell polls on a timer.** Sixty seconds, rather than pushing. The events that produce a
  notification are minutes apart at most, so a standing websocket per user would buy nothing
  noticeable — but if the client ever wants live updates, this is where that decision lives.
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
- ~~**File replacement:**~~ **Answered 11 August 2026: a member may replace their own file.** Built.
  Admins may replace anything, matching the delete rule — an admin can already delete and re-upload,
  so refusing the one-step version would only be theatre. Two things the client has not been asked:
  whether *version history* should be visible (superseded bytes are kept but unreachable, see §8),
  and whether replacing should notify anyone the way deleting notifies every admin. Today it does
  not; it is audited as `file_replaced`.
- **Retention:** how long do soft-deleted files stay restorable before purge? Currently indefinite.
- **Additional admins:** how should a second administrator be created — an existing admin promoting a
  member, or seeding only? Approval deliberately never changes a role, so today it takes SQL.
