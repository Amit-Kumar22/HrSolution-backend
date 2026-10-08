# HR Solution — Manpower Supply & HR Services Platform

Backend REST API for an Indian HR solutions and manpower supply business: client
and site management, manpower requisitions, recruitment, worker records,
deployment, attendance, payroll with statutory deductions (PF / ESI / PT), GST
invoicing and compliance tracking.

**Status: Phase 3 (Public website APIs & enquiries) complete.** See [Delivery phases](#delivery-phases).

---

## Tech stack

| Layer | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 4.1.1 (Spring Framework 7.0.9, Spring Security 7.1.1) |
| Build | Maven (wrapper included — use `./mvnw`) |
| Persistence | Spring Data JPA, Hibernate 7.4.5, MySQL 8 |
| Migrations | Flyway 12.4.0 — **the only thing that changes the schema** |
| Mapping | MapStruct 1.6.3 (compile-time), Lombok |
| JSON | Jackson 3.1.5 (note: `tools.jackson.*`, not `com.fasterxml.jackson.*`) |
| API docs | springdoc-openapi 3.1.1 → Swagger UI |
| Testing | JUnit 6, Mockito 5, AssertJ, Testcontainers 2 (real MySQL) |

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | **21** | Boot 4 needs 17+; this project targets 21 |
| MySQL | 8.x | Or use the one in `docker-compose.yml` |
| Docker | any recent | **Required to run the integration tests** |

### JDK 21 and `JAVA_HOME`

Your shell currently has `JAVA_HOME` pointing at **Zulu 17**, so plain `mvn`
builds would fail on `release 21`. JDK 21 is installed via Homebrew. Point
`JAVA_HOME` at it:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

Add that to `~/.zshrc` to make it permanent. In IntelliJ, also set
**Project Structure → Project SDK → 21** and
**Settings → Build Tools → Maven → Runner → JRE → 21**.

Verify:

```bash
./mvnw -version      # should report Java version: 21.x
```

---

## Getting started

### Option A — your local MySQL (the default)

This is the `local` profile and mirrors how `ems-office-project` is set up.

```bash
# 1. Create the database (once)
mysql -u root -p -e "CREATE DATABASE hrsolution_local_db \
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. Configure
cp .env.example .env
#    then edit .env and set DB_PASSWORD to your MySQL root password

# 3. Run
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
set -a && source .env && set +a
./mvnw spring-boot:run
```

Flyway creates the schema on first start. No `CREATE TABLE` by hand, ever.

### Option B — everything in Docker

```bash
cp .env.example .env
docker compose up -d --build
```

| Service | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| MailHog (read sent email) | http://localhost:8025 |
| MySQL | `localhost:3307` |

MySQL is published on **3307**, not 3306, so it never collides with a local
MySQL server. Both can run at the same time.

### Option C — app locally, database in a throwaway container

No database setup at all. Run `main` in
`src/test/java/com/hrsolution/support/TestHrSolutionApplication.java`; it starts
a disposable MySQL via Testcontainers, migrates it, and throws it away on exit.
Needs Docker running.

---

## Build and test

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

./mvnw test       # unit tests only — fast, no Docker needed
./mvnw verify     # everything, including Testcontainers integration tests (needs Docker)
./mvnw package    # build the jar
```

Unit tests are `*Test`; integration tests are `*IT` and run under Failsafe, so
`./mvnw test` stays quick and Docker-free.

> If `./mvnw verify` hangs or reports `Could not find a valid Docker environment`,
> start Docker Desktop and retry.

---

## Environment variables

Full annotated list in [`.env.example`](.env.example). No secret, password, key
or statutory rate is committed to this repository.

| Variable | Used by | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | all | `local` (default), `dev`, `prod` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | all | **No fallback in `dev`/`prod`** — a missing value stops startup on purpose |
| `CORS_ALLOWED_ORIGINS` | all | Comma-separated. Empty = none allowed (fails closed) |
| `MAIL_*` | all | Defaults target MailHog; no auth, no TLS |
| `CACHE_TYPE` | all | `caffeine` (default) or `none` |
| `SPRINGDOC_ENABLED` | prod | `false` in prod — Swagger exposes your whole API surface |
| `LOG_LEVEL_APP` | all | `INFO`, `DEBUG`, … |

`.env.example` also lists the variables that later phases will need (JWT keys,
field-encryption key, SUPER_ADMIN seed credentials) so the full set lives in one
place.

---

## Project layout

```
pom.xml                     Maven build
Dockerfile                  multi-stage: JDK to build, JRE to run, non-root
docker-compose.yml          MySQL 8.4 + MailHog + app
.env.example                every environment variable, annotated
docs/                       ER diagram, architecture decisions
src/main/java/com/hrsolution/
  HrSolutionApplication.java
  common/                   shared foundation — read this first
    config/                 security, OpenAPI, caching, async, scheduling, JPA auditing
    domain/                 BaseEntity, SoftDeletableEntity, AuditorAwareImpl
    error/                  ErrorCode, ApiException family, GlobalExceptionHandler
    repository/             SpecificationUtils — dynamic list filters
    util/                   MoneyUtils, DateUtils
    validation/             IndianFormats — GSTIN, PAN, IFSC, Aadhaar, UAN patterns
    web/                    ApiPaths, PageResponse, CorrelationIdFilter
  settings/                 company profile (the Phase 1 worked example)
    controller/ service/ repository/ entity/ dto/ mapper/
  auth/ user/ audit/        Phase 2: authentication, RBAC, security audit
  catalog/                  Phase 3: manpower categories and skill levels
  content/                  Phase 3: services, industries, testimonials + the public site
  enquiry/                  Phase 3: enquiry pipeline and the contact inbox
  document/                 Phase 3: upload metadata, authenticated downloads
  notification/             email (Thymeleaf templates, async with retry)
  seo/                      robots.txt and sitemap.xml
src/main/resources/
  application.properties            shared config
  application-{local,dev,prod}.properties
  db/migration/                     Flyway — V1 baseline, V2 auth/RBAC, V3 public site
  templates/email/                  Thymeleaf email templates
src/test/java/com/hrsolution/
  support/                  AbstractIntegrationTest, TestcontainersConfiguration
```

Packages are organised **by feature, not by layer** — everything about payroll
lives under `payroll/`, rather than being spread across `controllers/`,
`services/` and `entities/`. Each feature package has the same internal
structure: `controller`, `service`, `repository`, `entity`, `dto`, `mapper`.

---

## Conventions

These hold across the whole codebase. The `settings` module is a complete worked
example of all of them.

**Entities.** Extend `BaseEntity` (id + `created_at`/`updated_at`/`created_by`/
`updated_by` + `@Version`), or `SoftDeletableEntity` for business records that
must never be hard-deleted. Lombok is limited to `@Getter`/`@Setter`/`@Builder`/
`@RequiredArgsConstructor` — never `@Data` on an entity.

**Never return an entity from a controller.** Request and response DTOs only,
as records, mapped by MapStruct.

**Schema changes go through Flyway only.** `ddl-auto=validate` makes the app
refuse to start if an entity and its table disagree — which is how the
integration tests catch a mismatched migration.

**Money is `BigDecimal`**, 2 decimal places, `RoundingMode.HALF_UP`, via
`MoneyUtils`. Never `double`.

**Time** is stored as UTC `Instant` and displayed in `Asia/Kolkata` via
`DateUtils`. The API emits ISO-8601 UTC; `dd-MM-yyyy` formatting is a
presentation concern.

**Errors** are RFC 7807 `ProblemDetail`, built in exactly one place
(`GlobalExceptionHandler`). To add a case: add an `ErrorCode`, throw an
`ApiException` subclass. Every response carries `errorCode`, `timestamp`,
`path` and `correlationId`; validation failures add an `errors` array.

**Lists are always paginated** — return `PageResponse<T>`, never a bare `List`.
Filters are built with `SpecificationUtils`, whose helpers no-op when their
argument is null, so filters can be chained unconditionally.

### Adding a feature — the short version

1. `V<n>__<description>.sql` in `db/migration` — create the table with the six audit columns.
2. Entity extending `BaseEntity`/`SoftDeletableEntity`, column lengths matching the migration.
3. Repository extending `JpaRepository` (add `JpaSpecificationExecutor` if it needs filtering).
4. Request/response DTO records, with Bean Validation and `IndianFormats` patterns.
5. MapStruct mapper — copy the ignore-list pattern from `CompanySettingsMapper` for update methods.
6. Service, `@Transactional` (`readOnly = true` for reads).
7. Controller under `ApiPaths.V1`, with `@Valid` and `@Tag`/`@Operation`.
8. A unit test for the service and an `*IT` for the endpoint.

Run `./mvnw verify` — if the migration and entity disagree, every `*IT` fails
with a schema-validation error naming the offending column.

---

## API documentation

Swagger UI: http://localhost:8080/swagger-ui.html · raw document:
`/v3/api-docs`. Disabled in the `prod` profile.

Error responses look like this:

```json
{
  "type": "https://api.hrsolution.local/errors/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request contains 2 invalid field(s).",
  "instance": "/api/v1/settings/company",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-10-06T06:45:12.113Z",
  "path": "/api/v1/settings/company",
  "correlationId": "7f3c1e0a-9b2d-4e51-8c7a-1f0b3d5e9a22",
  "errors": [
    { "field": "gstin", "message": "must be a valid 15-character GSTIN, e.g. 27AABCS1234A1Z5", "rejectedValue": "NOT-A-GSTIN" }
  ]
}
```

Every request gets an `X-Correlation-Id` (echoed if you supply one). It appears
on every log line for that request, so a user reporting a failure can quote it
and you can `grep` straight to the cause.

---

## Authentication

Two tokens, deliberately delivered differently.

| | Access token | Refresh token |
|---|---|---|
| Format | RS256 JWT | opaque, 256-bit random |
| Lifetime | **15 min** | **7 days** (30 with "remember me") |
| Delivered in | JSON response body | `HttpOnly; Secure; SameSite=Strict` cookie, path `/api/v1/auth` |
| Client should keep it | **in memory only** | nowhere — the browser handles it |
| Stored server-side | not at all | SHA-256 hash only |

The access token goes in `Authorization: Bearer <token>`. The refresh token is
never in a response body, so a script cannot read it — which means an XSS flaw
cannot steal long-lived access.

### Token rotation and theft detection

Every `POST /auth/refresh` consumes the presented token and issues a
replacement. All tokens from one sign-in share a `familyId` and each consumed row
records its successor.

If an **already-used** token is presented, two parties hold it — so it leaked.
The response is 401 and the **entire family is revoked**, forcing a fresh
sign-in that the attacker cannot complete without the password. Revoking only
the replayed token would leave the attacker holding whatever they rotated it
into.

The cost: a legitimate client that retries a refresh after a network timeout can
log itself out. That is the right trade for a system holding payroll and identity
documents.

### Other controls

- **Lockout** — 5 failed passwords locks the account for 15 minutes. The counter
  is not reset when the lock expires, so an attacker cannot get a fresh five
  every quarter hour.
- **Rate limiting** — per IP and per email, on login, registration, password
  reset and refresh. Returns 429 with `Retry-After`.
- **No account enumeration** — an unknown email and a wrong password return
  byte-identical responses, and a dummy BCrypt verification keeps the timing
  flat. `/auth/forgot-password` always reports success.
- **`tokenVersion`** — incremented on password change, role change and account
  disable, which invalidates every outstanding access token with no blacklist.
- **Audit trail** — every security event in `audit_logs`, queryable at
  `/api/v1/audit-logs`, tied to the request by `correlationId`.

### JWT signing keys

Local and test profiles generate an ephemeral RSA pair at startup — no setup
needed, but every token dies with the process. **Any other profile refuses to
start without configured keys:**

```bash
mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/jwt-private.pem
openssl rsa -pubout -in keys/jwt-private.pem -out keys/jwt-public.pem
export JWT_PRIVATE_KEY_LOCATION=file:./keys/jwt-private.pem
export JWT_PUBLIC_KEY_LOCATION=file:./keys/jwt-public.pem
```

`keys/` and `*.pem` are gitignored.

### The first administrator

```bash
export SUPER_ADMIN_EMAIL=admin@yourcompany.com
export SUPER_ADMIN_PASSWORD='<a strong password>'
```

Created at startup if absent, and never touched again — restarting does not reset
a password you have since changed. Seeded via code rather than Flyway because a
BCrypt hash cannot be computed in SQL, and committing a pre-computed one would
publish the credential.

### Demo accounts (`local` profile only)

Password for all of them: **`Demo@12345`**

| Email | Role |
|---|---|
| `superadmin@hrsolution.local` | SUPER_ADMIN |
| `admin@hrsolution.local` | ADMIN |
| `hr@hrsolution.local` | HR_RECRUITER |
| `ops@hrsolution.local` | OPERATIONS_MANAGER |
| `supervisor@hrsolution.local` | SITE_SUPERVISOR |
| `accounts@hrsolution.local` | ACCOUNTS |
| `client@hrsolution.local` | CLIENT |
| `worker@hrsolution.local` | WORKER |
| `candidate@hrsolution.local` | CANDIDATE |
| `pending.client@hrsolution.local` | CLIENT — `PENDING_APPROVAL`, sign-in blocked |
| `unverified@hrsolution.local` | CANDIDATE — `PENDING_VERIFICATION`, sign-in blocked |

Seeded by `DemoDataSeeder`, which is `@Profile("local")`. That annotation is the
only thing keeping a password from source control out of dev and production — do
not widen it.

Full permission matrix: [`docs/rbac.md`](docs/rbac.md).

---

## The public website API

Everything the marketing site needs, with no authentication. All of it is
gathered in `PublicSiteController` so the question "what can the internet see?"
is answered by reading one file.

| Endpoint | Returns |
|---|---|
| `GET /api/v1/public/company-profile` | Name, address, phone, GSTIN, PAN, logo URL, social links |
| `GET /api/v1/public/services` | Published service pages, ordered |
| `GET /api/v1/public/services/{slug}` | One service page; 404 if unpublished |
| `GET /api/v1/public/industries` | Industries served |
| `GET /api/v1/public/testimonials` | Published client quotes |
| `GET /api/v1/public/manpower-categories` | Active categories, for the enquiry dropdown |
| `GET /api/v1/public/files/{key}` | Logos and images — **public assets only** |
| `POST /api/v1/public/enquiries` | Manpower requirement form |
| `POST /api/v1/public/contact-messages` | Contact Us form |
| `GET /robots.txt`, `GET /sitemap.xml` | SEO; at the root, as crawlers require |

### What the public endpoints deliberately do not expose

- **Unpublished drafts.** Absent from the lists, and 404 by direct slug — a
  work-in-progress page cannot be read by guessing its URL.
- **Bank and statutory details.** `PublicCompanyProfileResponse` omits the bank
  account, IFSC, TAN and the PF/ESI/PT registration codes. A published bank
  account invites invoice fraud. GSTIN, PAN and CIN *are* included, since Indian
  companies must display them.
- **Private files.** `/public/files/{key}` serves only documents flagged as
  public assets. Anything else returns 404 rather than 403, so the route cannot
  confirm that a private document exists.
- **Internal notes** on enquiries, and the ids of contact messages.

### Spam protection on the two forms

Three layered signals, and a deliberate choice about what to do with a hit.

| Signal | How |
|---|---|
| Rate limit | 5 submissions/hour per IP, returns 429 with `Retry-After` |
| Honeypot | Send the hidden `website` field empty; a bot fills it |
| Timing | Optional `formRenderedAt` (epoch millis); under 2s is not human |
| Content | More than two links, or known spam phrases |

A suspected submission is **stored and flagged, never rejected**. A false
positive on the enquiry form throws away a real sales lead, whereas a flagged
row is just hidden from the default pipeline view and recoverable in one click.
The acknowledgement is identical either way, and only unflagged submissions send
the notification email.

Staff see the real pipeline at `GET /api/v1/enquiries`; add `includeSpam=true`
or `status=SPAM` to review what was caught, and the reason is in the internal
notes.

### SEO

`robots.txt` emits `Disallow: /` unless `app.site.seo-indexing-enabled=true`.
**Indexing is opt-in**: a staging host that gets indexed competes with
production for the same search terms and is awkward to undo.

`sitemap.xml` is generated from the published content, so it can never list a
page that does not exist. Set `app.site.base-url` to the address visitors
actually use — not this API's.

---

## File uploads

| Endpoint | Accepts |
|---|---|
| `POST /api/v1/settings/company/logo` | PNG/JPEG/GIF, 2 MB — needs `SETTINGS_MANAGE` |
| `POST /api/v1/services/{id}/hero-image` | PNG/JPEG/GIF, 2 MB — needs `CONTENT_MANAGE` |
| `POST /api/v1/testimonials/{id}/image` | PNG/JPEG/GIF, 2 MB — `?logo=true\|false` |
| `GET /api/v1/documents/{id}/download` | Authenticated download, any document |

**The file type is detected from the leading bytes, never from the name or the
`Content-Type` header.** Both are client-controlled, so validating them is
security theatre: upload HTML as `logo.png`, have it served back, and the
browser renders script on this application's origin. Renaming a file does not
get it past validation. SVG is deliberately not allowed — it is XML that can
carry script.

The uploaded file name never contributes to the storage path; it is replaced by
a UUID keeping only a sanitised extension, which makes traversal structurally
impossible and stops two workers' `aadhaar.pdf` overwriting each other.

Files live under `app.storage.local-path` (default `./uploads`, gitignored),
**outside any web-served directory** — private documents are reachable only
through the authenticated endpoint. Swap `StorageService` for an S3
implementation without touching a caller.

> **Virus scanning is not configured.** `VirusScanner` is a no-op by default;
> provide any other `VirusScanner` bean to enable it. Content-based type
> detection, the size cap and authenticated-only downloads are still enforced.

---

## Database backups

MySQL is the single source of truth — uploaded documents aside, everything is in
it. Logical dumps are sufficient at this scale.

```bash
# Nightly full dump, consistent without locking (InnoDB)
mysqldump --single-transaction --routines --triggers --events \
          --default-character-set=utf8mb4 \
          -u backup_user -p hrsolution_prod_db \
    | gzip > "/var/backups/mysql/hrsolution-$(date +%F).sql.gz"
```

```cron
# 02:30 daily, keep 30 days
30 2 * * * /usr/local/bin/hrsolution-backup.sh >> /var/log/hrsolution-backup.log 2>&1
35 3 * * * find /var/backups/mysql -name 'hrsolution-*.sql.gz' -mtime +30 -delete
```

Points worth being deliberate about:

- `--single-transaction` gives a consistent snapshot without blocking writes.
  Leave it out and a nightly backup will lock out the attendance screens.
- **Test a restore.** An untested backup is a guess:
  `gunzip -c dump.sql.gz | mysql -u root -p hrsolution_restore_test`.
- Keep at least one copy **off the database host** (object storage or another machine).
- Back up uploaded documents (`STORAGE_LOCAL_PATH`, default `./uploads`) too —
  they are not in MySQL.
- Statutory records (payroll, PF/ESI challans, invoices) must be retained for
  years under Indian law. Keep monthly archives, not just a 30-day window.
- From Phase 6, Aadhaar/PAN/bank columns are encrypted with
  `FIELD_ENCRYPTION_KEY`. **Back that key up separately from the database.**
  Losing it makes those columns permanently unreadable; storing it beside the
  dumps defeats the encryption.

---

## Delivery phases

| # | Phase | Status |
|---|---|---|
| 1 | Foundation — build, profiles, `common` module, Flyway, Docker, Swagger | **Done** |
| 2 | Auth & RBAC — users, roles, permissions, JWT access + refresh rotation with reuse detection, lockout, rate limiting, email verification, password reset, sessions, security audit | **Done** |
| 3 | Public website APIs & enquiries — company profile, services, industries, testimonials, enquiry/contact forms, file storage, SEO | **Done** |
| 4 | Clients, sites, contracts, rate cards, client approval | Next |
| 5 | Recruitment — jobs, candidates, applications pipeline, interviews | |
| 6 | Workers, documents (encrypted fields), deployment, requisition workflow | |
| 7 | Attendance & leave — supervisor entry, Excel upload, client approval, locking | |
| 8 | Payroll — configurable statutory engine, payslip PDF, bank/PF/ESI exports | |
| 9 | Billing & invoicing — GST, invoice PDF, payments, credit notes, ageing | |
| 10 | Compliance, reminders, notifications, dashboards, reports, audit viewer | |
| 11 | Hardening — TOTP 2FA, security & performance review, CI pipeline | |

---

## Further reading

- [`docs/er-diagram.md`](docs/er-diagram.md) — full target schema, with the phase that creates each table
- [`docs/rbac.md`](docs/rbac.md) — the role–permission matrix, the deliberate separations of duty, and how to change them
- [`docs/decisions.md`](docs/decisions.md) — why the stack is configured the way it is, including the Boot 4 / Jackson 3 / Testcontainers 2 gotchas and the three transaction-rollback bugs found in Phase 2
