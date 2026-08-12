# Phase 5 Handoff — Admin monitoring, reports, profile

Written 12 August 2026, handing over after Phase 4. Read [HANDOFF.md](HANDOFF.md) first — it is the
orientation and the four rules; this file is only what changed and what happens next.

---

## 1. Where things stand

Phase 4 is complete: **a document can be found, looked at, kept and revisited.** Search spans all 43
departments, PDFs and images render in the page, favourites and download history exist, and the
notifications the system has been writing since Phase 1 are finally readable.

| | |
|---|---|
| Backend unit tests | **89 pass** (was 48) |
| Backend integration tests | 51 exist — 35 from Phase 3 plus 16 new — **none of the new ones have run**, see §3 |
| Web | typecheck, lint and build clean |
| Seen in a browser | **Nothing from this phase.** No Docker, no database — see §3 |

### What landed

- `notification/`: list, unread count, mark-one, mark-all — and the bell in `AppShell`
- `DiscoveryService`: search, inline preview, favourites, download history, recent documents
- `Download` and `Favorite` entities; `downloads` rows are now written when a link is issued
- Web: search results with facets in the URL, a preview screen, Favorites, Downloads, a real
  notifications screen, stars in every file table, and a home dashboard with recent documents
- `RepositoryQueryDerivationTest` — see §2, it exists because of this machine's limitations
- `lib/queryKeys.ts` — one list of the caches a document change invalidates

---

## 2. Decisions you are inheriting

**Search is a native query, and must stay one.** The schema carries a GIN trigram index on
`file_name`. `ILIKE '%…%'` uses it; the JPQL equivalent compiles to `lower(file_name) like …`, which
is a different expression from the one the index was built on, so every search would scan the table.
It would pass its tests on a handful of rows and quietly stop scaling. The `cast(… as …)` wrappers
around each optional facet are not decoration either — PostgreSQL cannot infer the type of a null
parameter in a native statement and refuses to prepare it.

**Wildcards in a search term are escaped.** Typing `%` looks for a percent sign rather than returning
the archive.

**Preview and download differ by exactly one argument.** `presignedGet(key, ttl, filename)` attaches
a Content-Disposition when given a filename and omits it when passed null. Inline versus attachment
is that, and nothing else.

**A preview is not a download.** Previewing writes an audit entry but no `downloads` row. That table
answers "who has a copy of this"; counting glances would make it useless. If the client later asks
for view counts, they are a different question and belong in the audit log.

**`favorite` and `previewable` ride on every `FileView`.** Stars are resolved with one query per page
in `DiscoveryService.viewsOf`, never one per row. `previewable` is decided by
`FileResponses.isPreviewable`, beside the flag it fills in, so the list and the preview endpoint
cannot disagree about what is renderable.

**Someone else's notification is a 404, not a 403.** Which ids exist is not something that endpoint
should confirm, and to the caller a row they cannot touch may as well not exist.

**Favourites hide deleted documents; download history does not.** A favourite is a shortcut, and one
pointing at nothing is clutter — the row survives, so a restore brings it back. A history is a
record, and one that drops entries when a document is removed is not a record.

**A new kind of test exists because of this machine.** Derived repository methods
(`findByUserIdAndFileDeletedFalse`) are parsed from their names at context startup, so a misspelled
property compiles and then fails at boot — or only inside an integration test that needs Docker.
`RepositoryQueryDerivationTest` parses all 23 of them against their entities with Spring Data's own
`PartTree`, no database required. **Add a row when you add a derived finder.**

---

## 3. Do this before writing any Phase 5 code

This is not the usual "run the tests first" preamble. Two full phases of work now sit behind a
verification gap.

```bash
docker compose up -d
cd backend && ./mvnw verify        # 89 unit + 51 integration; 16 have never executed
cd admin-web && npm run dev        # then click through everything below
```

**Click through, in this order** — none of it has been seen by a human:

1. Sign in, look at the home dashboard: four tiles, recent documents, the pending-requests banner if
   you are an admin.
2. The bell in the header — delete a document as a member, then check the admin's badge and list.
   Open a notification; it should mark itself read and navigate to the document.
3. The search box: a fragment of a filename, then each facet, then the date bounds. The URL should
   carry the query — reload and confirm the results survive.
4. A PDF preview and an image preview. Then a `.doc`, which should offer a download instead.
5. Star something; check Favorites, then delete it and confirm it leaves the list; restore it as an
   admin and confirm it comes back.
6. Downloads, after downloading something. Delete that document and confirm the row stays, marked.

If something is wrong there it is Phase 4's bug, not yours. Fix it before building on top.

### Environment traps

| Symptom | Cause |
|---|---|
| Every Lombok getter is "cannot find symbol" | The JDK is newer than 21. Lombok's processor silently no-ops on JDK 24+. Build on **JDK 21**. |
| `JAVA_HOME is not defined correctly`, but a JDK is installed | It was pointing at the JDK's `bin`. It must point at the JDK **root**. |
| The IDE reports those errors while `./mvnw` compiles cleanly | The language server is not running Lombok. Trust Maven. |

The rest are in [HANDOFF.md §4](HANDOFF.md).

---

## 4. Phase 5 — the work

*Done when* an admin can answer "what has this member been doing", "what happened to this document"
and "how much is this system being used" without opening a database client.

Suggested order, each step leaving the build green:

### 4.1 My Profile

The smallest piece, and the only one a member sees.

- `GET /me`, `PATCH /me` (name, email, designation), `POST /me/password`
- `GET /me` is [HANDOFF.md §8](HANDOFF.md)'s standing gap: session restore currently goes through
  `/auth/refresh`, which mints a token as a side effect of asking who you are.
- Changing a password should bump `token_version` — `AuthService.resetPassword` already does, and
  this path must not be the one that forgets.
- **Editing a profile never returns an account to PENDING.** Approval happens once; see the plan.

### 4.2 Per-member activity

- `GET /admin/members/{id}/activity` — logins, uploads, downloads, deletions, interleaved
- Everything needed is already recorded: `audit_logs` for logins and deletions, `downloads` and
  `files` for the rest. This is a read across them, not new writing.
- The member detail route already exists in `App.tsx` as a placeholder.

### 4.3 Audit log viewer

- `GET /admin/audit-logs?actor=&action=&from=&to=` — paged, filterable
- `audit_logs` has indexes on `(actor_id, created_at)` and `(action, created_at)`; filter on those
  rather than on the JSONB metadata, which is unindexed.
- Read-only, with no delete: an audit trail you can edit is not one.

### 4.4 Reports and CSV export

- `GET /admin/reports` — uploads and downloads per department, per month, most-active members
- `GET /admin/stats` for the dashboard tiles
- Stream the CSV rather than building it in memory; a year of downloads is not a small list.
- Excel opens CSV in the system locale, so quote everything and write a UTF-8 BOM, or Tamil
  department names will arrive as mojibake on a clerk's machine.

### 4.5 Dashboard activity feed and static pages

The admin dashboard's recent-activity panel, plus Help, About and Privacy — all routed placeholders
today.

---

## 5. Already wired for you — do not rebuild it

| | |
|---|---|
| `audit_logs`, `downloads` | Both written and indexed; Phase 5 only reads them |
| `AuditAction` | Every action name is already declared — use the constants, do not invent strings |
| `PageResponse` | The wire shape of every paged list |
| `ClientIp.current()` | One derivation of the caller's address |
| `AppShell`, `components/ui/`, `lib/tones.ts` | The design system; a new screen inherits it by using them |
| `SkeletonRows` / `SkeletonCards` | Loading states are skeletons, not the word "Loading" |
| `invalidateFileLists` | Add any new document-bearing query key to its array |
| `RepositoryQueryDerivationTest` | Add a row per new derived finder |

---

## 6. Conventions that will bite you

The full list is [HANDOFF.md §6](HANDOFF.md). The ones that matter most in Phase 5:

- **Build response DTOs inside the transaction.** Every association is lazy; a DTO assembled after
  the transaction closes throws `LazyInitializationException` and returns 500. This has cost time in
  two phases already, and activity feeds join across four entities.
- **`AuditService.record` vs `recordDurable`** — join the caller's transaction for successful
  actions, commit separately for rejected attempts.
- **Guard `/admin/**` on the class, not the method.** `AdminUserController` carries one
  `@PreAuthorize` for its whole surface so a new endpoint cannot be added unguarded. Do the same in
  any new admin controller, and add a test that a member gets 403.
- **Server state on the web goes through TanStack Query** — the `react-hooks/set-state-in-effect`
  rule rejects a hand-rolled fetch hook. `UploadDialog` is the one deliberate exception and says why.
- **Look and feel is part of "done"** — see [HANDOFF.md §5](HANDOFF.md). Reports and tables are
  high-traffic admin surfaces; skeletons, motion and the shared palette are not optional on them.
- **A clean build is not a working screen.** Three phases running, the build has been green on
  screens nobody had looked at. Run the app.

---

## 7. Definition of done for Phase 5

- [ ] `./mvnw verify` green — including Phase 4's 16 unverified integration tests
- [ ] `npm run lint && npm run typecheck && npm run build` clean
- [ ] Every new screen clicked through in a browser and reachable from the header navigation
- [ ] An admin can open a member and see what they have done
- [ ] A report exports to CSV that opens correctly in Excel, with Tamil department names intact
- [ ] A member can edit their profile and change their password, and the change signs other
      sessions out
- [ ] `docs/HANDOFF.md` §2 and §7 updated, and the next handoff written

---

## 8. Open questions — worth asking the client now

The first two are the longest poles and neither has moved in three phases.

- **SMS gateway:** MSG91 or Twilio, and who owns the India DLT registration? It blocks real OTP
  delivery in UAT and has a lead time. Still unstarted.
- **Hosting:** NIC/MeghRaj or AWS? Court documents on a commercial cloud need written sign-off.
- **Retention:** how long do soft-deleted files stay restorable before purge? Currently indefinite,
  and the bytes are never removed from storage.
- **Additional admins:** an existing admin promoting a member, or seeding only? Today it takes SQL.
  Phase 5 builds the member-management screens, so this is the natural moment to settle it.
- **Version history:** superseded bytes are kept but unreachable. Should a replacement be
  recoverable through the UI, and should it notify anyone the way a deletion notifies every admin?
- **What a report is for:** "reports with CSV export" is in the plan, but nobody has said what
  question the office actually needs answered. Ask before building five charts nobody opens.
