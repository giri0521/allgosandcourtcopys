# Phase 6 Handoff — Hardening and UAT

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
cd backend && ./mvnw verify      # 121 unit + 67 integration; 32 have never executed
cd admin-web && npm run dev
```

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
| Every Lombok getter is "cannot find symbol" | The JDK is newer than 21. Lombok's processor silently no-ops on JDK 24+. Build on **JDK 21**. |
| `JAVA_HOME is not defined correctly`, but a JDK is installed | It was pointing at the JDK's `bin`. It must point at the JDK **root**. |
| The IDE reports those errors while `./mvnw` compiles cleanly | The language server is not running Lombok. Trust Maven. |
| `npx playwright install chromium` fails to download | Use the installed browser instead: `chromium.launch({ channel: 'msedge' })`. |

---

## 4. Phase 6 — the work

*Done when* the client has signed off on a walkthrough, the system holds up at 150 concurrent users,
and nothing in the security review is outstanding.

### 4.1 Security review

The application's own rules have tests; this is about everything around them.

- Re-read [HANDOFF.md §1](HANDOFF.md) and confirm each of the four rules still holds end to end
  after five phases of change. The tests say yes; a person should agree.
- Dependency audit: `./mvnw versions:display-dependency-updates` and `npm audit`.
- Confirm the bucket is not publicly readable in whatever environment you point at.
- Rate limits: OTP send and verify are bounded per mobile number. **Login and the API generally are
  not.** A per-IP limit in front of `/auth/**` is the obvious gap.
- Response headers: HSTS, `X-Content-Type-Options`, a CSP for the web app. None are set today.
- Check that no endpoint leaks an internal id or a stack trace — `include-stacktrace` is `never`,
  but confirm it in the environment you deploy.

### 4.2 Load test

- k6, 150 concurrent users, p95 < 500ms on list endpoints — the figure in the plan.
- The two to watch: `GET /files/search` (the trigram index must be doing the work — check the query
  plan, do not assume) and `GET /admin/reports` (half a dozen aggregates per load, §8).
- Presigned URLs mean document bytes never pass through the application, so throughput on downloads
  is object storage's problem rather than Spring's. Confirm that in the numbers.

### 4.3 Accessibility

- Keyboard: every dialog, the star buttons, the chart's table view, the file table's hover actions
  (they are `focus-within`-visible for exactly this reason — verify it works).
- Screen reader: the skeletons announce politely, the bell has a label with a count, the chart has a
  text alternative and a table.
- Colour: the palette was validated for the chart. The status badges have not been through the same
  check — run them through `scripts/validate_palette.js` in the dataviz skill.

### 4.4 Responsive QA

360 / 768 / 1440 px, on every screen. The known weak points, by construction:

- The header at 360px — nav wraps, and search is hidden below `md` with no other way to reach it.
  Decide whether that is acceptable or whether search needs an icon at small widths.
- Wide tables: the members list, the reports table and the file table all scroll inside
  `overflow-x-auto`. Confirm the page itself never scrolls sideways.
- The chart's fixed viewBox scales; check the month labels at 360px.

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

- [ ] `./mvnw verify` green — including the 32 integration tests that have never run
- [ ] Every screen clicked through at 360 / 768 / 1440 px
- [ ] CSV exports opened in Excel with Tamil department names intact
- [ ] Load test at 150 concurrent users meeting p95 < 500ms on list endpoints
- [ ] Keyboard and screen-reader pass on every dialog and interactive control
- [ ] Security review closed, with rate limiting and response headers decided one way or the other
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
