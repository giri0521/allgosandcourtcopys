# ALLGOSANDCOURTCOPYS

A Document Management System for a government/court office: securely store, organize and distribute
official documents — Government Orders, Court Orders, Circulars, Contracts, Acts & Rules — across
43 departments, for an internal user base of 100+ people.

**Full specification: [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md)** — screens, schema,
API surface, phases, security and test strategy.

---

## What it does

Both Admins and Members self-register. **No account works until an Admin approves it** — that approval
is the only access gate in the system. Once approved, a user can view, download and upload documents in
**any** department. A member may delete a file they uploaded, but must give a reason, which is sent to
every Admin as a notification.

| | Member | Admin |
|---|---|---|
| View & download any department | ✅ | ✅ |
| Upload to any department | ✅ | ✅ |
| Delete own upload (reason required) | ✅ | ✅ |
| Delete anyone's file | — | ✅ |
| Approve/reject registrations | — | ✅ |
| Manage departments & folders | — | ✅ |
| Monitor all member activity, reports, audit logs | — | ✅ |

**Login:** mobile number is the identity. OTP is mandatory on the **first login of each calendar day**;
for the rest of that day, password or OTP both work.

## Stack

| Layer | Choice |
|---|---|
| Web app (both roles) | React 18 + TypeScript + Vite + Tailwind, responsive |
| Backend API | Java 21 + Spring Boot 3.3 |
| Database | PostgreSQL 16, Flyway migrations |
| File storage | S3-compatible — MinIO in dev, S3 or on-prem MinIO in prod |
| Auth | JWT (access + refresh) via Spring Security; OTP over a pluggable SMS provider |

There is no mobile app. `admin-web/` is a single responsive SPA that serves both roles.

## Layout

```
backend/            Spring Boot API
  src/main/java/com/allgos/dms/
    auth/ user/ department/ folder/ file/ notification/ audit/ report/ common/
    (each with controller / service / repository / entity / dto)
  src/main/resources/db/migration/   Flyway: schema + 43-department seed + first admin
admin-web/          React SPA (Admin + Member)
  src/app/          router
  src/features/     one folder per feature area
  src/lib/          api client
docs/               plan, wireframes, department list, branding
docker-compose.yml  Postgres + MinIO for local development
```

## Getting started

Prerequisites: **JDK 21**, **Maven 3.9+**, **Node 22.12+**, **Docker**.

```bash
cp .env.example .env

# 1. infrastructure
docker compose up -d              # Postgres :5432, MinIO :9000 (console :9001)

# 2. backend — Flyway applies the schema and seeds 43 departments + the first admin
cd backend && mvn spring-boot:run # http://localhost:8080

# 3. web app
cd admin-web && npm install && npm run dev   # http://localhost:5173
```

In development `OTP_PROVIDER=mock`, so OTP codes are printed to the backend log instead of being sent
over SMS — no gateway account needed to work on the app.

### Checks

```bash
cd backend   && mvn verify        # unit + Testcontainers integration tests
cd admin-web && npm run lint && npm run typecheck && npm test && npm run build
```

## Status

Scaffold in place: project structure, database schema and seeds, configuration, local infrastructure,
CI. Feature work follows the phases in
[docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) — registration and approval first.
