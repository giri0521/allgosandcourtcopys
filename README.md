# ALLGOSANDCOURTCOPYS

Document Management System for a government/court office — securely store, organize and
distribute Government Orders, Court Orders, Circulars, Contracts and Acts & Rules across
departments, with role-based access for Admins and Viewers.

## Repo Structure

```
backend/       Spring Boot REST API (Java) — auth, departments, folders, files, permissions
admin-web/     React + TypeScript admin panel
mobile-app/    React Native + TypeScript viewer app (Android + iOS)
docs/          Client requirements, wireframes, implementation plan
```

## Roles

- **Admin** — manages departments, folders, files, viewer accounts, permissions, reports, audit logs.
- **Viewer** — self-registers, is approved by an Admin, then browses/downloads only the folders
  they've been assigned (view-only or view+download).

See [`docs/ALLGOSANDCOURTCOPYS_Implementation_Plan_1.pdf`](docs/ALLGOSANDCOURTCOPYS_Implementation_Plan_1.pdf)
for the full requirements, architecture, database schema, API design and phased delivery plan.

## Tech Stack

| Layer | Choice |
|---|---|
| Mobile app (Viewer) | React Native + TypeScript |
| Admin web panel | React + TypeScript |
| Backend API | Java + Spring Boot |
| Database | PostgreSQL |
| File storage | S3 / MinIO |
| Auth | JWT (Spring Security), OTP via SMS gateway |

## Getting Started

Setup instructions per component will live in each folder's own README as they're scaffolded.

## Team

- Project owner / client-facing: TBD
- Developer: TBD
