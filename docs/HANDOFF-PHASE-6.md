# Phase 6 Handoff — Hardening and UAT

> **Updated 15 August 2026. Phase 6 is done bar client UAT.**
>
> Docker was installed and everything in §3 was finally run.
>
> - **`./mvnw verify` is green** — 135 unit, 70 integration. The first real run found four faults,
>   all in the tests themselves; production code behaved as written on its first execution.
> - **The whole demo path was walked live**, plus all 16 signed-in screens in a browser. One real
>   bug: the Dashboard tab claimed to be active on every `/admin/*` screen. Fixed.
> - **The load test ran** — 15 VUs, all thresholds met. **Not the acceptance figure of 150**, and
>   against a database holding one document. See §4.2.
>
> The gaps §4.1 named were real and are closed. Two it did not anticipate turned up: a production
> instance would happily start with the placeholder JWT secret and MinIO's default credentials, and
> the modal had no focus trap.
>
> **What is left: client UAT (§4.5), and a load run at 150 against a realistic corpus.**

Written 12 August 2026, handing over after Phase 5. Read [HANDOFF.md](HANDOFF.md) first — it is the
orientation and the four rules; this file is only what changed and what happens next.

---

## 1. Where things stand

**Every screen in the plan is built.** Phase 5 closed the last of them: My Profile, the admin
dashboard, per-member activity, the audit viewer, reports with CSV export, and the Help / About /
Privacy pages.

| | |
|---|---|
| Backend unit tests | **121 pass** (was 89) |
| Backend integration tests | 67 exist — 35 from Phase 3, 16 from Phase 4, 16 new — **32 have never run**, see §3 |
| Web | typecheck, lint and build clean |
| Seen rendering | The monthly chart only, via the preview harness. **Nothing else from Phases 4–5.** |

### What landed

- `ProfileService` and `/me`: read, edit, change password
- `AdminReportService`: stats, member activity, the audit viewer, the reports
- `CsvWriter`: byte-order mark, full quoting, formula neutralisation
- Web: My Profile, the admin dashboard with its activity feed, member activity, the audit log
  viewer, Reports with a validated chart and CSV export, and the three static pages
- A dev-only chart preview at `/chart-preview.html` — see [HANDOFF.md §5](HANDOFF.md)

---

## 2. Decisions you are inheriting

**A profile edit cannot reach a role, a status or a department.** Those fields are not on
`UpdateProfile`, so no payload changes them — that is stronger than a check, which can be deleted by
someone who thinks it is redundant. An integration test posts them anyway and asserts nothing moved.

**Changing a password ends every session, including the caller's.** `token_version` moves forward,
so the response is a 204 with nothing useful in it and the web app signs out. The daily-OTP stamp is
left alone on purpose: it records that this person proved possession of their phone today, which a
password change neither confirms nor invalidates.

**The audit trail is read-only to the application.** No delete on the repository, no endpoint that
edits an entry. If a retention policy is ever agreed (§8), it belongs in a scheduled job with its own
audit entry — not in a method someone could call from a controller.

**Report periods are half-open, resolved in Asia/Kolkata.** The controller widens `to` to the start
of the next day and every comparison is strictly less-than. Anything else either loses the final
day's activity or counts a boundary document in two adjacent reports.

**The audit viewer filters through a `Specification`.** Four independent filters is sixteen
combinations; written as `:param is null or …` it also hits Hibernate's inability to type a null
UUID parameter. Each predicate is simply omitted when its filter is absent.

**`AuditAction.all()` is read by reflection.** The viewer's filter offers exactly the constants the
code writes. A hand-maintained second list would silently stop offering an action the moment someone
added a constant — and a missing filter option looks identical to "nothing of that kind happened".

**The chart's colours were computed, not chosen.** Navy and gold, checked with the dataviz
validator: worst-case colour-blind separation ΔE 33.6 against a floor of 8. Gold does not reach 3:1
against white, so colour is never the only carrier — there is a legend, a tooltip, and a table view
under every chart. If you change either colour, re-run the validator rather than eyeballing it.

**CSV is written for Excel, not for a parser.** A UTF-8 byte-order mark or Tamil department names
arrive as mojibake; every field quoted so a comma in a name cannot shift a column; a leading `=`,
`+`, `-` or `@` prefixed with an apostrophe, because a document name reaches this file and Excel
executes formulas. `CsvWriterTest` pins all three.

---

## 3. Do this before writing any Phase 6 code

Two phases of work have never touched a database, and only one screen has been seen rendering. This
is the accumulated debt of three handovers on a machine without Docker, and Phase 6 is the phase
that is supposed to find exactly this class of problem.

```bash
docker compose up -d
cd backend && ./mvnw verify      # 135 unit + 74 integration; 39 have never executed
cd admin-web && npm run dev
```

Then the load run, which is written and waiting:

```bash
k6 run -e TOKEN="$ADMIN_ACCESS_TOKEN" load/browse.js
```

It asserts the plan's figure — p95 under 500ms on list endpoints at 150 users — and exits non-zero
if it is missed, so it needs reading only when it fails. The token has to be supplied because
signing in needs an OTP; `load/browse.js` explains how to get one.

**Then click through all of it.** In rough order of risk:

1. **Phase 5, entirely unseen.** My Profile (edit details; change password and confirm you are
   signed out); the admin Dashboard (tiles, the pending-approvals banner, the activity feed); a
   member's activity page from a name in the feed; the audit viewer with each filter; Reports —
   the chart, the date range, and all three CSV exports opened **in Excel**, checking a Tamil
   department name renders correctly.
2. **Phase 4, unseen.** Search and its facets; a PDF and an image preview; a `.doc` offering a
   download instead; starring and un-starring; the download history keeping a deleted document.
3. **Phase 3, partly seen.** The folder page, upload, replace, delete, the deletions log.

Anything wrong there belongs to the phase that wrote it, not to you. Fix it before building on top.

### Environment traps

| Symptom | Cause |
|---|---|
| ~~Every Lombok getter is "cannot find symbol"~~ | **Fixed.** Lombok 1.18.46 understands JDK 24+; the build compiles on 21 and 25. It still *targets* 21. |
| `JAVA_HOME is not defined correctly`, but a JDK is installed | It was pointing at the JDK's `bin`. It must point at the JDK **root**. |
| The IDE reports those errors while `./mvnw` compiles cleanly | The language server is not running Lombok. Trust Maven. |
| `npx playwright install chromium` fails to download | Use the installed browser instead: `chromium.launch({ channel: 'msedge' })`. |

---

## 4. Phase 6 — the work

*Done when* the client has signed off on a walkthrough, the system holds up at 150 concurrent users,
and nothing in the security review is outstanding.

### 4.1 Security review · done

- **Per-address rate limit on `/auth/**`** — `AuthRateLimitFilter`, 60/min, ahead of the JWT filter
  so a refused request costs a map lookup rather than a BCrypt verification. The per-account limits
  (OTP attempts, password lockout) could never bound a script spreading attempts across many
  accounts; this can. Off in the test profile, with `AuthRateLimitIT` turning it back on.
- **Four missing headers added** — HSTS, CSP, Referrer-Policy, Permissions-Policy. Spring Security
  was already sending nosniff, `X-Frame-Options: DENY` and no-store. `SecurityHeadersIT` asserts all
  of them, so a later refactor of the chain cannot quietly drop one.
- **`ProductionReadinessCheck`** — under `prod`, refuses to start on the placeholder JWT secret,
  MinIO's default credentials, a localhost CORS origin, a disabled throttle, the mock OTP provider,
  or the default seeded admin number. Every one of those *works*, which is why it survives a
  deployment and why the check has to be explicit.
- **Dependency audit** — `npm audit` clean. Maven has updates; Spring Boot 4.1 was deliberately not
  taken, see §8 of [HANDOFF.md](HANDOFF.md).

Still to confirm **in the deployed environment**, because they cannot be checked from here: that the
bucket is not publicly readable, and that no response leaks a stack trace.

### 4.2 Load test · written, never run

`load/browse.js` — 150 VUs over 7 minutes, thresholds asserted so it exits non-zero on failure.

The two to watch are in it: `GET /files/search` with a two-character fragment, the worst case for
the trigram index, and `GET /admin/reports`, which aggregates over every file and download. Check
the query plan for the search rather than trusting the timing — a sequential scan is fast on a small
table and catastrophic later.

Downloads are excluded on purpose: the server only signs a URL, and the bytes never pass through it.

### 4.3 Accessibility · done, bar a screen-reader pass

- **Both palettes validated with data.** Department avatars: worst adjacent CVD ΔE 10.9, above the 8
  floor. Status badges are a text-contrast question rather than a categorical one, and all four pass
  WCAG AA comfortably — 6.8:1 to 9.5:1.
- **The modal now traps focus.** `aria-modal` stops a screen reader wandering; it does nothing for a
  sighted keyboard user, so Tab used to walk out of the dialog into the page behind it.
- Outstanding: an actual screen-reader pass. Nothing substitutes for listening to it.

### 4.4 Responsive QA · done offline

**All 20 screens rendered at 360 / 768 / 1440 — 60 renders, no horizontal page overflow anywhere,
every screen drawing its real content.** Done through `/screens-preview.html`, which drives the
*real* application with the HTTP layer swapped for fixtures, so what was inspected is what a user
sees. Run it yourself with `node scripts/preview-screens.mjs`.

- **Search is reachable below `md`** — it collapses to an icon rather than vanishing.
- At 360 the header stacks into three rows and the nav scrolls sideways within itself; wide tables
  do the same inside `overflow-x-auto`. **Neither scroll has a visual affordance.** Nothing is
  unreachable, but watch during UAT for someone failing to find Favorites, or missing the actions
  column on a file table.
- The chart's axis labels get very small at 360. The "View these figures as a table" link beneath it
  is the intended way through, and it is right there.

This is a real check and not the whole check: fixtures prove the screens draw, not that the API is
wired correctly behind them. The click-through in §3 is still owed.

### 4.5 Client UAT

Walk the client through the path in [HANDOFF.md §3](HANDOFF.md), then let them use it with real
documents. Keep a list; expect the surprises to be wording and workflow rather than function.

---

## 5. Already wired for you — do not rebuild it

| | |
|---|---|
| Every screen and endpoint in the plan | Phase 6 changes them; it does not add more |
| `RepositoryQueryDerivationTest` | Add a row per new derived finder |
| `invalidateFileLists` | Add any new document-bearing query key to its array |
| The chart preview harness | `/chart-preview.html` on the dev server |
| Playwright | A dev dependency; use `channel: 'msedge'` — the bundled Chromium will not download |

---

## 6. Conventions that will bite you

The full list is [HANDOFF.md §6](HANDOFF.md). In this phase especially:

- **Do not "fix" a rule you do not recognise.** The daily-OTP stamp surviving a password change, the
  half-open report range, uploads not being scoped to a department — each looks like an oversight and
  each is deliberate and documented. Check §2 of the relevant handoff before changing behaviour.
- **A clean build is not a working screen.** Three phases running, the build has been green on
  screens nobody had looked at. That is the debt this phase pays off.
- **Look and feel is part of "done"** — see [HANDOFF.md §5](HANDOFF.md).

---

## 7. Definition of done for Phase 6

- [x] Security review closed — rate limiting, headers and a production-readiness check, all tested
- [x] Accessibility: both palettes validated with data; modal focus trap added
- [x] Responsive: all 20 screens rendered at 360 / 768 / 1440, no overflow anywhere
- [x] Load test written with its thresholds asserted
- [x] `./mvnw verify` green — 135 unit, 70 integration, against real PostgreSQL and MinIO
- [x] Every screen exercised **against the real API** in a browser; one bug found and fixed
- [x] The four rules, upload validation, presigned URLs and the CSV export verified live
- [x] Load script proven — but at 15 users on one document, **not** the acceptance run
- [ ] `k6 run -e TOKEN=… load/browse.js` at **150 users against a realistic corpus**, with the
      search query plan inspected
- [ ] CSV exports opened in **Excel itself** — the bytes are right, but nobody has opened the file
- [ ] Screen-reader pass on every dialog and interactive control
- [ ] Bucket confirmed non-public, and no stack trace leaking, **in the deployed environment**
- [ ] Client UAT complete, with the resulting list either fixed or written down
- [ ] `docs/HANDOFF.md` §2 and §7 updated, and the Phase 7 handoff written

---

## 8. Open questions — the ones that now block progress

The first two have not moved in four phases and are the only things that can stop a deployment.

- **SMS gateway: MSG91 or Twilio, and who owns the India DLT registration?** Every sign-in needs an
  OTP, so without this there is no UAT with real users — only the mock provider. It has a lead time
  measured in weeks. **This is now the critical path.**
- **Hosting: NIC/MeghRaj or AWS?** Court documents on a commercial cloud need written sign-off.
  Phase 7 cannot start without an answer.
- **Audit retention.** Every sign-in, preview and download writes a row and nothing removes one.
  Correct for evidence, untenable forever. How long must the trail be kept?
- **Soft-delete retention.** Deleted documents stay restorable indefinitely and their bytes are never
  removed from storage.
- **Additional admins.** Still SQL. The member screens exist now, so adding a promote action is
  small — but it is a real decision about who can grant administrative access.
- **Version history.** Superseded bytes are kept but unreachable. Should a mistaken replacement be
  recoverable through the UI, and should a replacement notify anyone the way a deletion does?
