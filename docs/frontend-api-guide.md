# HR Solution — Frontend API Guide (Phases 1–5)

**For:** whoever builds the web client.
**Covers:** everything the backend exposes today — 158 endpoints across 119 paths.
**Verified against:** a live server, not just the source. Every status code, field name and
permission in this document was checked by calling the running API.

> **Phase 5 (recruitment) is new.** Jobs, candidates, the application funnel and interviews
> are in §5 Panel B6–B9, the public careers page in Panel A, and the candidate portal in
> Panel D — which is no longer a stub. One permission changed: `CANDIDATE` **lost**
> `JOB_READ`, so candidates browse vacancies through the anonymous endpoints only. See §14.

> **Read §3 and §4 before writing any code.** They describe the auth handshake and the
> error envelope, which every other screen depends on. §5 is the panel-by-panel screen
> plan — which CRUD goes where. §6 is the endpoint reference. §12 lists what is *not*
> built yet, so you don't design screens against APIs that don't exist.

---

## Table of contents

| § | Section |
|---|---------|
| 1 | [Run it locally](#1-run-it-locally) |
| 2 | [Test accounts](#2-test-accounts) |
| 3 | [Authentication — the full handshake](#3-authentication--the-full-handshake) |
| 4 | [Error envelope](#4-error-envelope) |
| 5 | [The panels — which CRUD goes where](#5-the-panels--which-crud-goes-where) |
| 6 | [Endpoint reference](#6-endpoint-reference) |
| 7 | [Pagination, sorting, filtering](#7-pagination-sorting-filtering) |
| 8 | [Enums for dropdowns](#8-enums-for-dropdowns) |
| 9 | [Validation rules (Indian formats)](#9-validation-rules-indian-formats) |
| 10 | [File uploads and downloads](#10-file-uploads-and-downloads) |
| 11 | [Permission → screen matrix](#11-permission--screen-matrix) |
| 12 | [Not built yet](#12-not-built-yet) |
| 13 | [Gotchas](#13-gotchas-things-that-will-bite-you) |
| 14 | [What changed in Phase 5](#14-what-changed-in-phase-5) |

---

## 1. Run it locally

```bash
cp .env.example .env     # then set DB_PASSWORD to your MySQL root password
./mvnw spring-boot:run
```

The app reads `.env` itself (via `spring.config.import` in `application.properties`), so
there is no shell export and no IDE plugin needed.

The port is **8080** by default. `.env.example` sets `SERVER_PORT=8081`, so if you copied
it as instructed above, use 8081. Check the startup log line if unsure.

| Thing | URL (assuming 8081) |
|---|---|
| API base | `http://localhost:8081/api/v1` |
| Swagger UI | `http://localhost:8081/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8081/v3/api-docs` |
| Health | `http://localhost:8081/actuator/health` |

**Generate your API client from `/v3/api-docs`.** Every schema has an explicit name
(`Client`, `ClientSite`, `RateCard`, `EnquirySubmitRequest`, …) so `openapi-generator`
or `orval` will produce clean types.

### CORS

Credentials are allowed, so wildcards are impossible — every origin must be listed
exactly, **scheme and port included**. The `local` profile already permits:

```
http://localhost:5173   http://127.0.0.1:5173    (Vite)
http://localhost:3000   http://127.0.0.1:3000    (CRA / Next.js)
```

Any other origin gets **403 on the preflight**, before your request reaches a controller.
If you run the dev server on a different port, add it to `app.cors.allowed-origins` in
`application-local.properties`. `localhost` and `127.0.0.1` are *different origins* to a
browser — that is why both are listed.

`X-Correlation-Id` is the only exposed response header. Log it; it's how backend errors
get traced.

---

## 2. Test accounts

The `local` profile seeds nine accounts, one per role. **Password for all: `Demo@12345`**

| Email | Role | Permissions | Use it to test |
|---|---|---:|---|
| `superadmin@hrsolution.local` | SUPER_ADMIN | 41 | everything, incl. settings + role editing |
| `admin@hrsolution.local` | ADMIN | 34 | everything except settings + role editing |
| `ops@hrsolution.local` | OPERATIONS_MANAGER | 14 | clients, sites, contracts, rate cards |
| `hr@hrsolution.local` | HR_RECRUITER | 11 | read-only on clients |
| `accounts@hrsolution.local` | ACCOUNTS | 12 | read-only on clients |
| `supervisor@hrsolution.local` | SITE_SUPERVISOR | 5 | almost nothing exists yet (Phase 6) |
| `client@hrsolution.local` | CLIENT | 9 | the client portal |
| `worker@hrsolution.local` | WORKER | 4 | nothing exists yet (Phase 7) |
| `candidate@hrsolution.local` | CANDIDATE | 3 | nothing exists yet (Phase 5) |

Two extra accounts exist for testing non-happy paths:

| Email | Status | What happens on login |
|---|---|---|
| `pending.client@hrsolution.local` | `PENDING_APPROVAL` | **403** — awaiting staff approval |
| `unverified@hrsolution.local` | `PENDING_VERIFICATION` | **403** — email not confirmed |

> These are `local`-profile only. On a real server only `SUPER_ADMIN_EMAIL` /
> `SUPER_ADMIN_PASSWORD` from the environment are seeded, once.

### Sample client data

The local database also has one worked-through client so you have something real to
render — the seeder does **not** create this, it was added by hand:

| | |
|---|---|
| Client | `CLI-00001` — Bharat Textiles Private Limited, Pune, `INTRA_STATE` |
| Contract | `BT/HRS/2026-27/09`, `ACTIVE`, 8.5% of wages |
| Rate card | Security Guard, ₹18,000 wage → ₹22,500 billing, from 2026-10-01, current |
| Portal user | `client@hrsolution.local` is linked to it, so Panel C works end to end |

It has **no sites yet** — add one through the UI you're building; that's a good first
write-path test. To start clean:
`DELETE FROM client_users; DELETE FROM rate_cards; DELETE FROM client_contracts; DELETE FROM clients;`

---

## 3. Authentication — the full handshake

Two tokens, deliberately different in kind.

| | Access token | Refresh token |
|---|---|---|
| Format | JWT, RS256 signed | opaque random string |
| Lifetime | **15 minutes** | 30 days (`rememberMe: true`) / session |
| Where it lives | **your memory** — a variable, a store | `hrs_refresh` **HttpOnly cookie** |
| Sent as | `Authorization: Bearer <token>` | automatically by the browser |
| Readable by JS | yes | **no, by design** |

**Do not put the access token in `localStorage`.** Keep it in memory. On a page reload you
get a new one from `/auth/refresh`, which works because the cookie survives the reload.

### 3.1 Sign in

```http
POST /api/v1/auth/login
Content-Type: application/json

{ "email": "admin@hrsolution.local", "password": "Demo@12345", "rememberMe": true }
```

`200 OK`:

```json
{
  "accessToken": "eyJraWQiOiJ1T19IYjNBUnJiX205...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "expiresAt": "2026-10-10T05:02:13.379249Z",
  "user": {
    "id": 2, "email": "admin@hrsolution.local",
    "firstName": "Asha", "lastName": "Patil", "fullName": "Asha Patil",
    "phone": "9876510001", "status": "ACTIVE", "emailVerified": true,
    "roles": ["ADMIN"],
    "permissions": ["APPLICATION_MANAGE", "AUDIT_VIEW", "CLIENT_READ", "..."],
    "lastLoginAt": "2026-10-09T11:20:04Z"
  }
}
```

plus

```
Set-Cookie: hrs_refresh=<opaque>; Path=/api/v1/auth; Max-Age=2591999;
            HttpOnly; SameSite=Strict
```

**`user.permissions` is your authorisation source for the UI.** Build the menu and show
or hide buttons from this array — never from `roles`. Roles are an implementation detail
that an admin can re-point at different permissions at runtime; permissions are what the
backend actually enforces. See §11.

Your HTTP client **must** send `credentials: 'include'` (fetch) or
`withCredentials: true` (axios), or the cookie is never stored and refresh will never work.

### 3.2 Refresh

```http
POST /api/v1/auth/refresh      ← no body, no Authorization header; the cookie is the credential
```

Returns the same `AuthTokenResponse` shape and a **new** cookie.

**The old refresh token is invalidated on every call.** This is rotation, and it has a
consequence you must design for: if two tabs refresh at the same moment, the second one
presents a token that was just consumed, the backend treats it as a **stolen token
replay**, and it revokes the entire token family — every session for that user, on every
device, instantly. The user is thrown out everywhere.

So: **serialise refreshes.** One in-flight refresh promise, shared by all callers.

```ts
let inFlight: Promise<string> | null = null;

async function refresh(): Promise<string> {
  if (!inFlight) {
    inFlight = fetch('/api/v1/auth/refresh', { method: 'POST', credentials: 'include' })
      .then(r => { if (!r.ok) throw new Error('refresh failed'); return r.json(); })
      .then(d => { setAccessToken(d.accessToken); return d.accessToken; })
      .finally(() => { inFlight = null; });
  }
  return inFlight;
}
```

Call it on: app start, any `401`, and ideally a timer at ~13 minutes.

If refresh itself returns `401`, the session is genuinely over — clear state, go to login.

### 3.3 Sign out

| Endpoint | Effect |
|---|---|
| `POST /auth/logout` | ends **this** session; clears the cookie |
| `POST /auth/logout-all` | ends **every** session on every device |

### 3.4 Sessions screen (every signed-in user)

| Method | Path | Does |
|---|---|---|
| `GET` | `/auth/sessions` | lists active sessions — IP, user agent, created, expires, `current` |
| `DELETE` | `/auth/sessions/{sessionId}` | revokes one |

Render as "Where you're signed in", with the `current: true` row badged and non-revocable.

### 3.5 Self-registration

| Endpoint | Who | Resulting status |
|---|---|---|
| `POST /auth/register/candidate` | job seeker | `PENDING_VERIFICATION` |
| `POST /auth/register/client` | client company | `PENDING_VERIFICATION` → then `PENDING_APPROVAL` |

Both need `consentGiven: true` and a 10-digit Indian mobile. Response:

```json
{ "userId": 42, "email": "...", "status": "PENDING_VERIFICATION", "message": "..." }
```

The client journey is two-gated, and you need a screen for each state:

```
register → PENDING_VERIFICATION → (clicks email link) → PENDING_APPROVAL
         → (staff approves)      → ACTIVE → can sign in
```

A login attempt in either pending state returns **403**. Show the reason, not a generic
"wrong password" — the user did nothing wrong.

### 3.6 Email verification and password reset

| Endpoint | Body | Note |
|---|---|---|
| `POST /auth/verify-email` | `{ "token": "..." }` | token from the emailed link |
| `POST /auth/resend-verification` | `{ "email": "..." }` | rate limited |
| `POST /auth/forgot-password` | `{ "email": "..." }` | **always 200**, even for unknown emails |
| `POST /auth/reset-password` | `{ "token": "...", "newPassword": "..." }` | |
| `POST /auth/change-password` | `{ "currentPassword": "...", "newPassword": "..." }` | needs Bearer |
| `GET /auth/me` | — | re-reads the current user; use after a role change |

`forgot-password` returning 200 for unknown addresses is deliberate — it stops the form
being used to discover who has an account. **Your UI must not claim the email was sent to
a known address.** Say "If that address has an account, a reset link is on its way."

### 3.7 Lockout and rate limits

| Rule | Value |
|---|---|
| Failed sign-ins before lockout | **5** → locked on the 6th |
| Lockout duration | 15 minutes |
| `POST /auth/login` | 20/min per IP, 10/min per email |
| `POST /auth/register/*` | 5/hour per IP |
| `POST /auth/forgot-password` | 5/hour per IP |
| `POST /auth/refresh` | 30/min per IP |

Once locked, **even the correct password returns 403** until the window expires. Say so
explicitly, or users will think the system is broken.

A `429` carries a `Retry-After` header **and** a `retryAfterSeconds` field. Use it to
drive a live countdown rather than a bare error.

---

## 4. Error envelope

Every non-2xx is RFC 7807 `application/problem+json`. One shape, everywhere.

```json
{
  "type": "https://api.hrsolution.local/errors/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request contains 2 invalid field(s).",
  "instance": "/api/v1/public/enquiries",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-10-10T04:47:07.768486Z",
  "path": "/api/v1/public/enquiries",
  "correlationId": "b83b9451-7da6-4aa4-8b08-f79537c80c90",
  "errors": [
    { "field": "companyName", "message": "Company name is required" },
    { "field": "phone", "message": "must be a valid 10-digit Indian mobile number",
      "rejectedValue": "123" }
  ]
}
```

**Branch on `errorCode`, never on `detail`.** Wording changes; codes don't.

| `errorCode` | HTTP | What it means for the UI |
|---|---:|---|
| `VALIDATION_FAILED` | 400 | map `errors[]` onto form fields by `field` |
| `MALFORMED_REQUEST` | 400 | bad JSON — a client bug |
| `UNAUTHENTICATED` | 401 | refresh once, then redirect to login |
| `ACCESS_DENIED` | 403 | hide the control; you shouldn't have shown it |
| `RESOURCE_NOT_FOUND` | 404 | "not found" — also returned for *someone else's* record |
| `METHOD_NOT_ALLOWED` | 405 | client bug |
| `DUPLICATE_RESOURCE` | 409 | "that code/GSTIN is already used" |
| `CONCURRENT_MODIFICATION` | 409 | someone else saved first — reload and retry |
| `PAYLOAD_TOO_LARGE` | 413 | file over the cap (§10) |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | wrong `Content-Type` |
| `BUSINESS_RULE_VIOLATION` | 422 | **the important one** — see below |
| `RATE_LIMIT_EXCEEDED` | 429 | countdown from `retryAfterSeconds` |
| `DATA_INTEGRITY_VIOLATION` | 500 | backend bug — surface the `correlationId` |
| `INTERNAL_ERROR` | 500 | ditto |

**`422 BUSINESS_RULE_VIOLATION` is not a form error.** It means the request was
well-formed but the domain refused it: terminating a `DRAFT` contract, editing a rate card
that already took effect, approving a client that isn't pending. Show `detail` as a
toast or dialog — it is written for end users. Do not try to attach it to a field.

`rejectedValue` is omitted for sensitive fields (passwords, tokens) — don't rely on it
being present.

### 403 vs 404

A `404` can mean "exists, but not yours". Asking for another client's site returns
`404`, not `403`, so the API never confirms that an id exists. Don't write UI that
distinguishes them.

---

## 5. The panels — which CRUD goes where

Four distinct front-ends. They can be one app with role-based routing, or separate
bundles — the API doesn't care. What matters is that **a screen is only reachable if the
signed-in user holds the permission behind it**.

```
┌─ Panel A · Public website ──────── no login ───────────────────────────┐
│  marketing pages · enquiry form · contact form · registration         │
└───────────────────────────────────────────────────────────────────────┘
┌─ Panel B · Staff console ──────── 6 internal roles ────────────────────┐
│  Dashboard · Enquiries · Clients · Website CMS · Masters · Users ·     │
│  Settings · Audit · Jobs · Candidates · Pipeline · Interviews          │
└───────────────────────────────────────────────────────────────────────┘
┌─ Panel C · Client portal ──────── CLIENT role ─────────────────────────┐
│  My company · My sites · My contracts · My rates                       │
└───────────────────────────────────────────────────────────────────────┘
┌─ Panel D · Candidate portal ───── CANDIDATE role ──────────────────────┐
│  My profile · My CV · My applications · Withdraw                       │
│  (WORKER is still sign-in only — Phase 7. See §12.)                    │
└───────────────────────────────────────────────────────────────────────┘
```

---

### Panel A · Public website (no authentication)

Build these as server-rendered or statically generated pages — they're the SEO surface.

| Screen | CRUD | Endpoint |
|---|---|---|
| Header / footer / contact page | **R** | `GET /public/company-profile` |
| Services index | **R** | `GET /public/services` |
| Service detail | **R** | `GET /public/services/{slug}` |
| Industries we serve | **R** | `GET /public/industries` |
| Testimonials carousel | **R** | `GET /public/testimonials` |
| Enquiry form — category dropdown | **R** | `GET /public/manpower-categories` |
| **Enquiry form — submit** | **C** | `POST /public/enquiries` |
| **Contact Us — submit** | **C** | `POST /public/contact-messages` |
| **Careers — open vacancies** | **R** | `GET /public/jobs` |
| **Careers — one vacancy** | **R** | `GET /public/jobs/{slug}` |
| **Careers — apply** | **C** | `POST /public/jobs/{slug}/apply` |
| Candidate sign-up | **C** | `POST /auth/register/candidate` |
| Client sign-up | **C** | `POST /auth/register/client` |
| `robots.txt`, `sitemap.xml` | **R** | `GET /robots.txt`, `GET /sitemap.xml` |

#### The careers page

`GET /public/jobs` is **paged** and filterable by `categoryId` and `city` (exact,
case-insensitive). It returns only adverts that are `OPEN` **and** have not passed their
closing date — never drafts, held or filled roles.

`GET /public/jobs/{slug}` is a **404** once an advert is withdrawn, filled or lapsed, so a
stale link never shows a vacancy nobody can apply to. Handle that as "this role has
closed", not as an error.

**The public payload never names the client.** There is no `clientId`, `clientName`,
`siteId` or `status` field — not hidden, structurally absent. Clients in this industry
require that a vacancy not be attributed to them. Don't build UI expecting them.

Use the pre-composed labels rather than formatting the numbers yourself:

| Field | Example | When null |
|---|---|---|
| `salaryRangeLabel` | `"Rs 18000 - Rs 22000 per month"` | salary not disclosed |
| `experienceLabel` | `"1 year - 5 years"`, `"No experience required"` | no requirement stated |
| `skillLevelLabel` | `"Semi-skilled"` | never |
| `expiresOn` | `"2026-12-31"` | open until filled |

#### The apply form — `POST /public/jobs/{slug}/apply`

**`multipart/form-data` with two parts**, not JSON:

| Part | Content type | Required |
|---|---|---|
| `application` | `application/json` — a `PublicApplyRequest` | yes |
| `resume` | `application/pdf`, ≤10 MB | no |

```ts
const body = new FormData();
body.append('application', new Blob([JSON.stringify(payload)],
                                    { type: 'application/json' }));
if (file) body.append('resume', file);
await fetch(`/api/v1/public/jobs/${slug}/apply`, { method: 'POST', body });
// Do NOT set Content-Type — the browser adds the multipart boundary.
```

Required fields: `fullName`, `email`, `phone` (10-digit Indian mobile), and
**`consentGiven: true`** — DPDP Act consent to hold the CV. Anything else is a `400`.

**PDF only, checked by file content.** A Word document or HTML renamed to `.pdf` is
rejected. Validate `file.type` client-side for a fast message, but expect the server to be
stricter.

Same honeypot as the other public forms: render `website` hidden and empty, send
`formRenderedAt` as epoch millis on mount. A tripped check is **accepted with 201** and
flagged for staff review — never tell the submitter.

`201` returns a reference to show on the thank-you page:

```json
{ "reference": "APP-000123", "applicationId": 123, "jobTitle": "Security Guard",
  "status": "APPLIED", "message": "Thank you. Your application has been received..." }
```

A second application for the same job and email is a **409**, and the message **masks the
address** (`r***********e@example.com`) because whoever typed it may not own it. Render
the message as-is; don't substitute what the user entered.

Rate limited per IP — handle `429`.

Only published rows are returned. Images come back as ready-to-use absolute paths
(`heroImageUrl`, `logoUrl`, `photoUrl`) — don't build them yourself.

**Both public forms have a honeypot.** Render a field named `website`, visually hidden,
and leave it empty. Also send `formRenderedAt` as epoch millis when the form mounts:

```jsx
<input name="website" tabIndex={-1} autoComplete="off"
       style={{ position:'absolute', left:'-9999px' }} aria-hidden="true" />
```

A filled honeypot or an implausibly fast submission is **accepted with 201** and silently
flagged as spam for staff review. Never tell the submitter — that just teaches the bot.

Enquiry submit returns a reference to show on the thank-you page:

```json
{ "reference": "ENQ-2026-000123", "message": "Thank you. Our team will contact you..." }
```

Both forms are rate limited per IP. Handle `429`.

---

### Panel B · Staff console

Six roles share one console. Menu items appear per permission — this table is the spec:

| Menu | Permission | SUPER&nbsp;ADMIN | ADMIN | OPS | HR | ACCOUNTS | SUPERVISOR |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|
| Enquiries | `ENQUIRY_MANAGE` | ✅ | ✅ | — | — | — | — |
| Contact messages | `ENQUIRY_MANAGE` | ✅ | ✅ | — | — | — | — |
| Clients — view | `CLIENT_READ` | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| Clients — add/edit | `CLIENT_WRITE` | ✅ | ✅ | ✅ | — | — | — |
| Clients — approve | `CLIENT_APPROVE` | ✅ | ✅ | — | — | — | — |
| Website CMS | `CONTENT_MANAGE` | ✅ | ✅ | — | — | — | — |
| Master data | `CONTENT_MANAGE` | ✅ | ✅ | — | — | — | — |
| Users — view | `USER_READ` | ✅ | ✅ | — | — | — | — |
| Users — manage | `USER_MANAGE` | ✅ | ✅ | — | — | — | — |
| Roles & permissions | `ROLE_MANAGE` | ✅ | — | — | — | — | — |
| Company settings | `SETTINGS_MANAGE` | ✅ | — | — | — | — | — |
| Audit trail | `AUDIT_VIEW` | ✅ | ✅ | — | — | — | — |
| Jobs — view | `JOB_READ` | ✅ | ✅ | — | ✅ | — | — |
| Jobs — manage | `JOB_MANAGE` | ✅ | ✅ | — | ✅ | — | — |
| Candidates — view | `CANDIDATE_READ` | ✅ | ✅ | — | ✅ | — | — |
| Candidates — manage | `CANDIDATE_WRITE` | ✅ | ✅ | — | ✅ | — | — |
| Pipeline — view | `APPLICATION_READ` | ✅ | ✅ | — | ✅ | — | — |
| Pipeline + interviews | `APPLICATION_MANAGE` | ✅ | ✅ | — | ✅ | — | — |

Two separations are deliberate, not oversights:

- **ADMIN cannot edit roles or company settings.** That's what makes SUPER_ADMIN
  meaningful — an admin runs the business but cannot grant itself more power or change
  statutory configuration.
- **HR and ACCOUNTS get read-only clients.** They need to look things up, not re-paper
  the commercial relationship.
- **Recruitment is HR's, not operations'.** OPERATIONS_MANAGER has no job, candidate or
  application permission at all: they raise the demand for workers, recruitment fills it.

SITE_SUPERVISOR still has no screens. Their work (attendance) is Phase 7.

#### B1 · Enquiries — the sales pipeline

The most-used screen in the console. A Kanban board over `EnquiryStatus`, or a table with
a status filter.

| Action | CRUD | Endpoint |
|---|---|---|
| List / search / filter | **R** | `GET /enquiries` |
| Open one (incl. internal notes) | **R** | `GET /enquiries/{id}` |
| Move along the pipeline | **U** | `PATCH /enquiries/{id}/status` |
| Assign to a staff member | **U** | `PATCH /enquiries/{id}/assign` |
| Soft-delete | **D** | `DELETE /enquiries/{id}` |

Pipeline: `NEW → CONTACTED → QUALIFIED → CONVERTED → CLOSED`, plus `SPAM`.

Filters available: `search`, `status`, `categoryId`, `assignedToUserId`, `from`, `to`,
`includeSpam`, plus `page`/`size`/`sort`.

`includeSpam` defaults to **false** — spam is hidden unless asked for. Give staff a
"Show spam" toggle; honeypot-flagged submissions land there and are occasionally
false positives worth rescuing.

Both status and assign take an optional `note` (≤1000 chars) that is appended to the
internal history. Offer the box; it's how the pipeline stays useful.

#### B2 · Contact messages — the inbox

| Action | CRUD | Endpoint |
|---|---|---|
| List | **R** | `GET /contact-messages` |
| **Unread badge** | **R** | `GET /contact-messages/unread-count` |
| Open one | **R** | `GET /contact-messages/{id}` |
| Mark read / unread | **U** | `PATCH /contact-messages/{id}/read` |
| Mark replied | **U** | `PATCH /contact-messages/{id}/replied` |
| Flag / unflag spam | **U** | `PATCH /contact-messages/{id}/spam` |
| Soft-delete | **D** | `DELETE /contact-messages/{id}` |

Poll `unread-count` for the sidebar badge. Filters: `search`, `read`, `includeSpam`.

#### B3 · Clients — the core of the console

This is the richest area: a client has **users**, **sites**, **contracts** and **rate
cards** hanging off it. Four nested resources, four tabs.

**B3.1 Client list** — `GET /clients`

Returns `ClientSummary` (10 fields), sized for a table row: `clientCode`, `displayName`,
`gstin`, `billingCity`, `billingState`, `status`, `gstTreatment`, `onboardedOn`.

Filters: `search`, `status`, `stateCode`, `clientId`.

> `search` matches substrings, so `?search=client@x.com` can hit
> `pending.client@x.com` first. Don't assume the first result is an exact match.

**B3.2 Client detail** — one call for the whole overview screen

`GET /clients/{clientId}/detail` returns `ClientDetail`:

```json
{ "client": { ...24 fields... }, "users": [...], "sites": [...], "contracts": [...] }
```

Use this for the landing tab; use the narrower endpoints for the individual tabs.
`GET /clients/{clientId}` returns just the client.

| Action | CRUD | Endpoint | Permission |
|---|---|---|---|
| Create | **C** | `POST /clients` | `CLIENT_WRITE` |
| Read | **R** | `GET /clients/{clientId}` | `CLIENT_READ` |
| Read + children | **R** | `GET /clients/{clientId}/detail` | `CLIENT_READ` |
| Update | **U** | `PUT /clients/{clientId}` | `CLIENT_WRITE` |
| Change status | **U** | `PATCH /clients/{clientId}/status` | `CLIENT_WRITE` |
| Soft-delete | **D** | `DELETE /clients/{clientId}` | `CLIENT_WRITE` |

`ClientRequest` — 15 fields, only `legalName` required:

```
legalName*            ≤200
tradeName             ≤200        — shown in lists when set, else legalName
gstin                 15-char GSTIN
pan                   10-char PAN
cin                   21-char CIN
billingAddressLine1   ≤200
billingAddressLine2   ≤200
billingCity           ≤100
billingState          ≤100
billingStateCode      two digits "01"–"39"   ← drives GST, see below
billingPincode        6 digits, no leading 0
billingCountry        ≤100
industryId            FK → GET /industries
paymentTermsDays      0–365
notes                 ≤2000
```

Three response fields are **derived — read-only, never submit them**:

- **`clientCode`** — `CLI-00042`, generated from the id. Stable and human-quotable; use it
  in the page title and anywhere staff read an id aloud.
- **`displayName`** — `tradeName` when set, else `legalName`. Use it in every list.
- **`gstTreatment`** — `INTRA_STATE` / `INTER_STATE` / `UNKNOWN`, computed by comparing
  the client's `billingStateCode` with the company's own. This decides whether invoices
  carry **CGST+SGST** or **IGST**, so render it as a visible chip on the client header.
  `UNKNOWN` means a state code is missing somewhere — surface it as a warning, because it
  will block correct invoicing in Phase 9.

Plus `activeSiteCount` and `activeContractCount` for the overview cards.

`PATCH /clients/{clientId}/status` takes `{ "status": ..., "reason": "..." }` over
`PENDING_APPROVAL | ACTIVE | SUSPENDED | INACTIVE | REJECTED`. Always prompt for the
reason — it goes to the audit trail.

**B3.3 Client users tab** — who at the client can sign in

| Action | CRUD | Endpoint | Permission |
|---|---|---|---|
| List | **R** | `GET /clients/{clientId}/users` | `CLIENT_READ` |
| Link an existing CLIENT user | **C** | `POST /clients/{clientId}/users` | `CLIENT_WRITE` |
| Nominate primary contact | **U** | `PATCH /clients/{clientId}/users/{clientUserId}/primary-contact` | `CLIENT_WRITE` |
| Revoke / restore access | **U** | `PATCH /clients/{clientId}/users/{clientUserId}/active` | `CLIENT_WRITE` |

`POST` takes `{ "userId": 7, "designation": "Plant HR Head", "primaryContact": true }`.
The user **must already exist and hold the CLIENT role** — this links, it doesn't create.
So the form needs a user picker fed from `GET /users?role=CLIENT`, not a set of name
fields.

Note `{clientUserId}` is the id of the *link*, not of the user.

**B3.4 Client registration approval** — the gate that matters

A self-registered client sits at `PENDING_APPROVAL` with no client record. Staff must
approve it, which does three things at once: creates the `Client`, links the user to it,
and **activates the login**.

| Action | Endpoint | Permission |
|---|---|---|
| Approve + create client record | `POST /clients/approve-registration/{userId}` | `CLIENT_APPROVE` |
| Approve (user record only) | `PATCH /users/{userId}/approve-client` | `CLIENT_APPROVE` |
| Reject | `PATCH /users/{userId}/reject-client` | `CLIENT_APPROVE` |

**Use `POST /clients/approve-registration/{userId}` for the real workflow.** It is the
one that produces a usable client. The `/users/...` variants only move the user's status
and leave no client record behind.

Body is `ClientApproveRegistrationRequest`. `legalName` is required and pre-fills from the
user's `pendingCompanyName`:

```
legalName*  gstin  pan  billingCity  billingState  billingStateCode
designation  paymentTermsDays
```

Build the approval screen as a review form: pull the pending users with
`GET /users?status=PENDING_APPROVAL`, show `pendingCompanyName`, let staff correct the
legal name and add GSTIN, then submit. Returns `201` with the new client.

**B3.5 Sites tab**

A site is a physical location where workers are deployed. Its **own** state code governs
minimum wage, professional tax and licensing — which is why it's separate from billing
address, and why a client with plants in two states needs two sites.

| Action | CRUD | Endpoint | Permission |
|---|---|---|---|
| List for a client | **R** | `GET /clients/{clientId}/sites` | `CLIENT_READ` |
| Add | **C** | `POST /clients/{clientId}/sites` | `CLIENT_WRITE` |
| Read | **R** | `GET /sites/{siteId}` | `CLIENT_READ` |
| Update | **U** | `PUT /sites/{siteId}` | `CLIENT_WRITE` |
| Activate / deactivate | **U** | `PATCH /sites/{siteId}/active` | `CLIENT_WRITE` |
| Soft-delete | **D** | `DELETE /sites/{siteId}` | `CLIENT_WRITE` |

> Note the URL shape: **create and list are nested** under the client, but read, update
> and delete are **top-level** on `/sites/{siteId}`. Same for contracts and rate cards.

`ClientSiteRequest`: `siteCode*` (≤40, letters/digits/`_`/`-`), `siteName*` (≤160),
address fields, `stateCode`, `pincode`, `siteInchargeName/Phone/Email`, `active`.

`siteCode` is **accepted in any case and stored upper-cased** — so `pune-plant-1`
becomes `PUNE-PLANT-1`. Either upper-case it in the input as the user types, or show the
stored value back after save, so the change doesn't look like a bug.

`displayLabel` in the response is `"PUNE-PLANT-1 — Pune Manufacturing Unit"`, ready for
dropdowns.

**B3.6 Contracts tab**

| Action | CRUD | Endpoint | Permission |
|---|---|---|---|
| List (newest first) | **R** | `GET /clients/{clientId}/contracts` | `CLIENT_READ` |
| **Which contract bills a date** | **R** | `GET /clients/{clientId}/contracts/in-force?onDate=` | `CLIENT_READ` |
| Draft | **C** | `POST /clients/{clientId}/contracts` | `CLIENT_WRITE` |
| Read | **R** | `GET /contracts/{contractId}` | `CLIENT_READ` |
| Update a **draft** | **U** | `PUT /contracts/{contractId}` | `CLIENT_WRITE` |
| Activate | **U** | `PATCH /contracts/{contractId}/activate` | `CLIENT_WRITE` |
| Terminate | **U** | `PATCH /contracts/{contractId}/terminate` | `CLIENT_WRITE` |
| Attach signed PDF | **C** | `POST /contracts/{contractId}/document` | `CLIENT_WRITE` |
| Delete | **D** | `DELETE /contracts/{contractId}` | `CLIENT_WRITE` |

Lifecycle — **drive the buttons from `status`**:

```
DRAFT ──activate──> ACTIVE ──┬── endDate passes ──> EXPIRED
  │                          └── terminate ───────> TERMINATED
  └── editable / deletable        (needs terminatedOn* + reason*)
```

`PUT` only works on a `DRAFT`. Editing an `ACTIVE` contract returns **422** — that's
intentional: an active contract is a signed commercial document. Hide Edit once active.

`ClientContractRequest`: `contractNumber*` (≤60), `title`, `startDate*`, `endDate`,
`serviceChargeType*`, `serviceChargeValue*`, `paymentTermsDays`, `notes`.

`serviceChargeType` changes the meaning of `serviceChargeValue` — so change the input's
label, suffix and validation with it:

| Type | `serviceChargeValue` means | Input |
|---|---|---|
| `PERCENTAGE` | % of wages | `8.5` with a `%` suffix, max 100 |
| `FIXED_PER_WORKER` | rupees per worker per month | `1200.00` with a `₹` prefix |

Read-only helpers in the response:

- **`serviceChargeLabel`** — `"8.50% of wages"` / `"Rs 1200 per worker"`. **Display this**
  rather than formatting the raw number yourself.
- **`effectivePaymentTermsDays`** — the terms that actually apply: the contract's if set,
  otherwise the client's. Use this on invoices.
- **`inForceToday`** — `ACTIVE` *and* today inside the date range. The green/grey dot.
- **`daysUntilExpiry`** — `null` when open-ended. Badge it amber under 30.
- **`documentCount`** — how many files are attached.

`endDate: null` means open-ended. Say "Open-ended", not an empty cell.

`in-force?onDate=YYYY-MM-DD` answers "which contract do I bill this month against?" —
needed by the Phase 9 invoicing screens, and useful now as a sanity check on the detail
page.

**B3.7 Rate cards tab — dated history, not editable rows**

The subtlest model in the system. A rate card is **never overwritten**. Creating a new one
for the same category closes the previous one the day before the new one starts.

| Action | CRUD | Endpoint | Permission |
|---|---|---|---|
| List | **R** | `GET /clients/{clientId}/rate-cards` | `CLIENT_READ` |
| **Resolve the applicable rate** | **R** | `GET /clients/{clientId}/rate-cards/resolve` | `CLIENT_READ` |
| Create (supersedes current) | **C** | `POST /clients/{clientId}/rate-cards` | `CLIENT_WRITE` |
| Read | **R** | `GET /rate-cards/{rateCardId}` | `CLIENT_READ` |
| Correct a **future** card | **U** | `PUT /rate-cards/{rateCardId}` | `CLIENT_WRITE` |
| Delete a **future** card | **D** | `DELETE /rate-cards/{rateCardId}` | `CLIENT_WRITE` |

**`PUT` and `DELETE` only work on a card that has not taken effect yet.** A card that is
live or historical is payroll and billing evidence; changing it would silently restate
past invoices. Attempting it returns **422**. So in the table:

- `current: true` → show **"Supersede"** (a `POST` with a later `effectiveFrom`), not Edit
- `effectiveFrom` in the future → Edit and Delete are allowed
- `effectiveTo` set (historical) → read-only

Render as a timeline grouped by category, not a flat editable grid:

```
Security Guard  ████████ Apr 1 – Sep 30  ₹17,000 → ₹21,000   (superseded)
                ████████ Oct 1 – now     ₹18,000 → ₹22,500   ● current
Housekeeping    ████████ Oct 1 – now     ₹15,000 → ₹18,800   ● current
  └─ PUNE-PLANT-1 override  ₹16,000 → ₹20,000                ● current
```

`RateCardRequest`: `categoryId*`, `siteId`, `monthlyWage*`, `billingRate*`,
`otRatePerHour`, `shiftHours` (1–24), `effectiveFrom*`, `notes`.

**`siteId: null` means the rate covers every site of the client.** A card naming a site
**wins** over a client-wide card for the same category, because minimum wages differ by
state. Make the scope an explicit radio — "All sites" vs "This site only" — never an
easily-missed optional dropdown.

Derived response fields:

| Field | Meaning |
|---|---|
| `grossMarginPerWorker` | `billingRate − monthlyWage` |
| `billingBelowWage` | `true` when you'd bill less than you pay — **show a loud warning** |
| `siteSpecific` | `true` when tied to one site |
| `current` | `true` when this is the rate in force today |
| `effectiveTo` | `null` while current; a date once superseded |

`resolve?categoryId=&siteId=&onDate=` returns `ResolvedRateCard`:

```json
{ "rateCard": { ... }, "resolution": "SITE_SPECIFIC", "ambiguous": false }
```

`resolution` tells you *how* the rate was chosen and `ambiguous: true` warns that more
than one card could apply. Show both — a silently-picked wrong rate is a billing dispute.

> **Commercial note worth a design decision:** a CLIENT-role user reading their own rate
> cards sees `monthlyWage`, `billingRate` **and `grossMarginPerWorker`** — the identical
> payload staff see. This is deliberate and documented in `RateCard.java`: under the
> Contract Labour (Regulation and Abolition) Act the principal employer is liable for
> ensuring workers actually receive minimum wage, so a client who cannot see the wage
> cannot discharge that obligation — and the margin is arithmetic they could do anyway.
> **The backend will not hide it, so decide consciously whether Panel C renders that
> column.** I'd omit `grossMarginPerWorker` from the client-facing table while keeping
> both wage and billing rate visible.

#### B4 · Website CMS — `CONTENT_MANAGE`

Each resource has a draft/publish split: these endpoints return **everything including
unpublished**, while the `/public/*` equivalents return only published rows. Build a
preview by reading from here.

**Services** (`/services`)

| Action | CRUD | Endpoint |
|---|---|---|
| List (incl. drafts) | **R** | `GET /services` |
| Read | **R** | `GET /services/{id}` |
| Create | **C** | `POST /services` |
| Update | **U** | `PUT /services/{id}` |
| Publish / unpublish | **U** | `PATCH /services/{id}/publish` |
| Hero image | **U** | `POST /services/{id}/hero-image` |
| Delete | **D** | `DELETE /services/{id}` |

`ServiceRequest`: `slug*` (lowercase kebab-case, `^[a-z0-9]+(?:-[a-z0-9]+)*$`), `title*`
(≤160), `summary*` (≤400), `description` (**≤20000 — rich text**), `icon`, `metaTitle`
(≤160), `metaDescription` (≤320), `displayOrder`, `published`.

`slug` is the public URL. Auto-generate it from the title, show it, and warn hard before
changing it on a published page — inbound links break.

Give `metaTitle`/`metaDescription` their own "SEO" section with character counters;
they're what `sitemap.xml` and search results use.

**Industries** (`/industries`) — `GET`, `POST`, `PUT /{id}`, `DELETE /{id}`.
`IndustryRequest`: `slug*`, `name*`, `description` (≤600), `icon`, `displayOrder`,
`published`. No upload, no detail page.

**Testimonials** (`/testimonials`) — `GET` (paged), `POST`, `PUT /{id}`,
`PATCH /{id}/publish`, `POST /{id}/image`, `DELETE /{id}`.
`TestimonialRequest`: `clientName*` (≤120), `clientCompany`, `designation`,
`content*` (≤1500), `rating` (1–5), `displayOrder`, `published`.

Testimonials are **unpublished by default** — a quote about a real client should not go
live unreviewed. The response `attribution` is a pre-composed
`"Rajesh Kulkarni, Plant Head, Bharat Textiles"` — use it instead of concatenating.

`displayOrder` is "lower first" everywhere. A drag-to-reorder list that writes the index
back is the right control.

#### B5 · Master data — `CONTENT_MANAGE` to write, any signed-in user to read

These feed dropdowns across the whole app, including the public enquiry form.

**Manpower categories** — the kinds of worker supplied (12 seeded)

| Action | CRUD | Endpoint | Permission |
|---|---|---|---|
| List | **R** | `GET /manpower-categories` | authenticated |
| Read | **R** | `GET /manpower-categories/{id}` | authenticated |
| Create | **C** | `POST /manpower-categories` | `CONTENT_MANAGE` |
| Update | **U** | `PUT /manpower-categories/{id}` | `CONTENT_MANAGE` |
| Deactivate | **U** | `PATCH /manpower-categories/{id}/deactivate` | `CONTENT_MANAGE` |
| Reactivate | **U** | `PATCH /manpower-categories/{id}/activate` | `CONTENT_MANAGE` |

`ManpowerCategoryRequest`: `code*` (letters/digits/underscore, **stored upper-case**),
`name*`, `skillLevel*`, `description`, `displayOrder`, `active`.

**There is no delete — only deactivate.** A category is referenced by historical rate
cards and enquiries; removing it would orphan them. Deactivating hides it from new
enquiries while keeping history valid. So the UI needs an Active/Inactive toggle column,
not a bin icon.

`skillLevelLabel` in the response is the human form (`"Semi-skilled"`) of the
`skillLevel` enum (`SEMI_SKILLED`). Display the label, submit the enum.

**Skills** — capabilities a worker can hold (12 seeded)

| Action | CRUD | Endpoint | Permission |
|---|---|---|---|
| List | **R** | `GET /skills?includeInactive=` | authenticated |
| Create | **C** | `POST /skills` | `CONTENT_MANAGE` |
| Update | **U** | `PUT /skills/{skillId}` | `CONTENT_MANAGE` |
| Activate / deactivate | **U** | `PATCH /skills/{skillId}/active` | `CONTENT_MANAGE` |

`GET /skills` is **not paged** — a plain array. Default hides inactive.

**Category ↔ skills mapping**

| Action | CRUD | Endpoint | Permission |
|---|---|---|---|
| Which skills a category needs | **R** | `GET /manpower-categories/{categoryId}/skills` | authenticated |
| Replace that set | **U** | `PUT /manpower-categories/{categoryId}/skills` | `CONTENT_MANAGE` |

`PUT` is a **full replacement**: `{ "skillIds": [1, 4, 7] }`. Send the complete desired
set, not a delta — omitting an id removes it. A dual-list or multi-select checkbox panel
fits; incremental add/remove buttons do not.

#### B6 · Users — `USER_READ` / `USER_MANAGE` / `ROLE_MANAGE`

| Action | CRUD | Endpoint | Permission |
|---|---|---|---|
| List / search | **R** | `GET /users` | `USER_READ` |
| Read | **R** | `GET /users/{userId}` | `USER_READ` |
| Create staff account | **C** | `POST /users` | `USER_MANAGE` |
| Update profile | **U** | `PUT /users/{userId}` | `USER_MANAGE` |
| **Replace roles** | **U** | `PUT /users/{userId}/roles` | `ROLE_MANAGE` |
| Disable | **U** | `PATCH /users/{userId}/disable` | `USER_MANAGE` |
| Re-enable | **U** | `PATCH /users/{userId}/enable` | `USER_MANAGE` |
| Clear lockout | **U** | `PATCH /users/{userId}/unlock` | `USER_MANAGE` |
| Approve client reg. | **U** | `PATCH /users/{userId}/approve-client` | `CLIENT_APPROVE` |
| Reject client reg. | **U** | `PATCH /users/{userId}/reject-client` | `CLIENT_APPROVE` |
| **Self-test authorities** | **R** | `GET /users/permissions-check` | authenticated |

Filters: `search`, `status`, `role`.

**There is no user delete** — accounts are disabled, never removed, because they're
referenced by the audit trail. Use Disable.

`UserResponse` carries the moderation fields your table needs: `status`, `emailVerified`,
`locked`, `lockRemainingSeconds`, `failedAttempts`, `roles`, `pendingCompanyName`,
`lastLoginAt`, `lastLoginIp`.

Show `locked` as a distinct state from `DISABLED` — one is temporary and self-clearing,
the other is an administrative decision. Offer **Unlock** only when `locked` is true, and
show the countdown from `lockRemainingSeconds`.

Request bodies — note these take **role names, not ids**:

```jsonc
// POST /users  — create a staff account
{ "firstName": "Priya", "lastName": "Sharma", "email": "priya@hrsolution.local",
  "phone": "9876543210", "password": "Strong@123", "roles": ["HR_RECRUITER"] }

// PUT /users/{userId}  — profile only. Email and roles are NOT editable here.
{ "firstName": "Priya", "lastName": "Sharma", "phone": "9876543210" }

// PUT /users/{userId}/roles  — full replacement, role NAMES
{ "roles": ["ADMIN", "ACCOUNTS"] }

// PATCH /users/{userId}/disable
{ "reason": "Left the company on 31-10-2026" }
```

`PUT /users/{userId}` accepts **only** `firstName`, `lastName` and `phone`. There is no
endpoint to change a user's email — build the edit form with just those three fields plus
a separate role control.

`PUT /users/{userId}/roles` is a **full replacement** over the nine `RoleName` values.
Use a multi-select of names; `roles` must be non-empty.

> **Changing a user's roles invalidates their existing access tokens.** The next request
> from that user returns `401`, your client refreshes, and the new token carries the new
> permissions. That's by design — but it means *your own* session breaks if you edit your
> own roles. Call `GET /auth/me` after a role change affecting the current user and
> re-render the menu from the fresh `permissions` array.

`GET /users/permissions-check` returns the caller's own authorities. Handy for a
debug panel: "why can't I see this button?"

#### B7 · Roles & permissions — `ROLE_MANAGE` (SUPER_ADMIN only)

| Action | CRUD | Endpoint | Permission |
|---|---|---|---|
| List roles + their permissions | **R** | `GET /roles` | `USER_READ` |
| Read one | **R** | `GET /roles/{roleId}` | `USER_READ` |
| **Permission catalogue** | **R** | `GET /roles/permissions` | `USER_READ` |
| Replace a role's permissions | **U** | `PUT /roles/{roleId}/permissions` | `ROLE_MANAGE` |

No create or delete — all nine roles are `systemRole: true` and referenced by name in
application logic. The grid is roles × permissions with checkboxes; rows are fixed,
cells are editable.

`GET /roles/permissions` returns all 41 permissions with a `module` field — group the
columns by it (ATTENDANCE, AUDIT, BILLING, CLIENT, COMPLIANCE, CONTENT, DEPLOYMENT,
ENQUIRY, PAYROLL, RECRUITMENT, REPORT, REQUISITION, SELF, SETTINGS, USER, WORKER).

`PUT` is a full replacement over **permission names**, and unlike roles an empty array
*is* allowed — it strips the role of everything:

```json
{ "permissions": ["CLIENT_READ", "CLIENT_WRITE"] }
```

**Warn before saving.** This screen can lock everyone out of a feature, and the change
takes effect on the next token refresh for every affected user. A confirm dialog naming
the role and the count of users holding it is the minimum.

#### B8 · Company settings — `SETTINGS_MANAGE` (SUPER_ADMIN only)

| Action | CRUD | Endpoint | Permission |
|---|---|---|---|
| Read | **R** | `GET /settings/company` | **any signed-in user** |
| Update | **U** | `PUT /settings/company` | `SETTINGS_MANAGE` |
| Replace logo | **U** | `POST /settings/company/logo` | `SETTINGS_MANAGE` |

A single row — there's no list and no id.

> **`GET` is readable by every authenticated user**, including WORKER. Verified live. It
> returns only non-sensitive fields plus `stateCode`/`updatedAt`/`updatedBy` — no GSTIN,
> PAN or bank details — so it's safe to use as a general-purpose "company header" source
> anywhere in the app. `PUT` is locked to `SETTINGS_MANAGE`.

`UpdateCompanySettingsRequest` has 30 fields; only `legalName` is required. Group them
into tabs, because one flat form is unusable:

| Tab | Fields |
|---|---|
| Identity | `legalName*`, `tradeName`, `tagline`, `about` (≤2000) |
| Address | `addressLine1/2`, `city`, `state`, **`stateCode`**, `pincode`, `country` |
| Contact | `phone`, `alternatePhone`, `email`, `supportEmail`, `website` |
| Statutory | `gstin`, `pan`, `tan`, `cin`, `pfEstablishmentCode`, `esiEstablishmentCode`, `ptRegistrationNumber` |
| Banking | `bankName`, `bankBranch`, `bankAccountNumber`, `bankIfsc` |
| Social | `linkedinUrl`, `facebookUrl`, `twitterUrl`, `instagramUrl` |

**`stateCode` is the single most consequential field here.** Every client's
`gstTreatment` is computed by comparing their state code to this one. Changing it
re-classifies every client between CGST+SGST and IGST. Flag it in the UI.

Every pattern-validated field accepts **empty string** as well as a valid value
(`^$|^...$`) — so "not yet known" is allowed, and your form should not require them.

#### B9 · Audit trail — `AUDIT_VIEW`

`GET /audit-logs` — read-only, paged. Filters: `action`, `userId`, `userEmail`,
`entityType`, `successful`, `from`, `to`.

26 `action` values, from `LOGIN_SUCCESS` and `ACCOUNT_LOCKED` through
`TOKEN_REUSE_DETECTED`, `ROLE_PERMISSIONS_CHANGED`, `CLIENT_APPROVED` to plain
`CREATE`/`UPDATE`/`DELETE`. Offer the filter as a grouped multi-select.

`oldValues` and `newValues` are **JSON strings** — parse and diff them for the detail
drawer rather than dumping raw text.

`successful: false` rows are the security view: failed sign-ins, blocked attempts,
detected token reuse. Worth a dedicated saved filter. `TOKEN_REUSE_DETECTED` means a
refresh token was replayed and a whole session family was revoked — surface it loudly.

Every row carries `correlationId`, matching the `X-Correlation-Id` you logged client-side.

#### B10 · Jobs — `JOB_READ` / `JOB_MANAGE`

Held by SUPER_ADMIN, ADMIN and HR_RECRUITER. **Not** OPERATIONS_MANAGER: operations raise
the demand for workers, recruitment fills it.

| Action | CRUD | Endpoint | Permission |
|---|---|---|---|
| List (all statuses) | **R** | `GET /jobs` | `JOB_READ` |
| Read | **R** | `GET /jobs/{jobId}` | `JOB_READ` |
| **Pipeline counts** | **R** | `GET /jobs/{jobId}/pipeline` | `APPLICATION_READ` |
| Create | **C** | `POST /jobs` | `JOB_MANAGE` |
| Update | **U** | `PUT /jobs/{jobId}` | `JOB_MANAGE` |
| Change status | **U** | `PATCH /jobs/{jobId}/status` | `JOB_MANAGE` |
| Record an application | **C** | `POST /jobs/{jobId}/applications` | `APPLICATION_MANAGE` |
| Delete | **D** | `DELETE /jobs/{jobId}` | `JOB_MANAGE` |

Filters: `search` (title, slug, city), `status`, `categoryId`, `clientId`.

Lifecycle — **drive the buttons from `status`**:

```
DRAFT ──publish──> OPEN ──┬── ON_HOLD ──> OPEN      (client paused, not cancelled)
  │                       ├── FILLED               (all openings taken)
  │                       └── CLOSED               (withdrawn without hiring)
  └── invisible publicly        only OPEN is on the careers page
```

**A job is always created as `DRAFT`**, whatever you send. Publishing is a separate,
audited call — an advert created straight into `OPEN` would go live before anyone read it
back. So the create form needs a "Save draft" button, and publishing is a second action.

`JobRequest`: `slug*` (lowercase kebab), `title*`, `summary`, `description` (≤20000, rich
text), `categoryId*`, `clientId`, `siteId`, `location`, `city`, `state`, `openings` (≥1),
`salaryMin`, `salaryMax`, `experienceMinMonths`, `experienceMaxMonths`, `shift`,
`expiresOn`, `metaTitle`, `metaDescription`, `displayOrder`.

Rejected with **422**: an inverted salary or experience range, a closing date in the past,
a `siteId` without a `clientId`, or a site belonging to a different client. Validate the
ranges client-side so the user finds out before submitting.

`slug` is the public URL. Auto-generate it from the title and **warn hard before changing
it on a live advert** — every inbound link breaks.

**Openings are counted, not decremented.** Three read-only fields:

| Field | Meaning |
|---|---|
| `openingsCommitted` | applications at `SELECTED`, `OFFERED` or `JOINED` |
| `openingsRemaining` | `openings − openingsCommitted`; **can go negative** |
| `applicationCount` | every application, any status |

A job with 3 openings and 3 selected candidates is full before anyone starts. Negative
`openingsRemaining` means over-committed — allowed (a client really can cut a requirement
after offers went out) but worth a warning badge.

`publiclyVisible` is the live/not-live dot: `OPEN` **and** not past `expiresOn`. `expired`
is true whenever the date has passed, *whatever the status says* — expiry is evaluated on
read, not swept by a nightly job. **Publishing a lapsed advert is a 422.**

`DELETE` is refused with **422** once applications exist — deleting would orphan their
history. Offer "Close" instead.

#### B11 · Candidates — `CANDIDATE_READ` / `CANDIDATE_WRITE`

| Action | CRUD | Endpoint | Permission |
|---|---|---|---|
| List | **R** | `GET /candidates` | `CANDIDATE_READ` |
| Read (incl. notes) | **R** | `GET /candidates/{candidateId}` | `CANDIDATE_READ` |
| Create | **C** | `POST /candidates` | `CANDIDATE_WRITE` |
| Update | **U** | `PUT /candidates/{candidateId}` | `CANDIDATE_WRITE` |
| Upload CV | **U** | `POST /candidates/{candidateId}/resume` | `CANDIDATE_WRITE` |
| Flag / clear spam | **U** | `PATCH /candidates/{candidateId}/spam?spam=` | `CANDIDATE_WRITE` |
| Delete | **D** | `DELETE /candidates/{candidateId}` | `CANDIDATE_WRITE` |

Filters: `search` (name, email, phone, city), `preferredCategoryId`, `source`,
`hasLogin`, `includeSpam`.

**Email is the identity key and is not editable.** `PUT` with a different address returns
**422**. Someone who applies four times over two years must be one candidate with four
applications, or the recruiter can't see they were interviewed and rejected last year.
Render the email read-only on the edit form.

A second candidate with an existing address is a **409** naming the existing record —
surface it as a link to that candidate, not a generic error.

**`consentGiven` must be true to create.** It's the DPDP basis for holding the CV. For a
walk-in, make it an explicit checkbox the recruiter ticks, not a default.

`source` defaults to **`WALK_IN`** for staff-created candidates, not `WEBSITE` —
attributing a walk-in to the website overstates the channel that costs money.

Spam-flagged candidates are **hidden unless `includeSpam=true`**. Give staff a toggle and
a "Not spam" action (`PATCH .../spam?spam=false`) — a honeypot false positive is a real
applicant.

`DELETE` is a soft delete and is how a consent withdrawal is honoured. Refused with
**422** once the person has joined: the recruitment record is then evidence the hire was
vetted, which outlives consent to hold marketing data.

#### B12 · Applications — the pipeline board (`APPLICATION_READ` / `APPLICATION_MANAGE`)

| Action | CRUD | Endpoint | Permission |
|---|---|---|---|
| List / board | **R** | `GET /applications` | `APPLICATION_READ` |
| Read | **R** | `GET /applications/{id}` | `APPLICATION_READ` |
| **Detail + trail + interviews** | **R** | `GET /applications/{id}/detail` | `APPLICATION_READ` |
| Move along the funnel | **U** | `PATCH /applications/{id}/status` | `APPLICATION_MANAGE` |
| Assign a recruiter | **U** | `PATCH /applications/{id}/assign` | `APPLICATION_MANAGE` |
| Record on someone's behalf | **C** | `POST /jobs/{jobId}/applications` | `APPLICATION_MANAGE` |

Filters: `jobId`, `candidateId`, `status`, `assignedToUserId`, **`unassigned=true`**,
`from`, `to`, `search` (candidate name, email, phone).

**There is deliberately no delete.** An application that should go away is `WITHDRAWN` or
`REJECTED`. Deleting would break the constraint that stops a candidate applying twice, and
remove the history a payroll or compliance dispute is argued from.

The funnel:

```
APPLIED → SCREENING → SHORTLISTED → INTERVIEW_SCHEDULED → SELECTED → OFFERED → JOINED
             ↕                              ↓                  ↓         ↓
          ON_HOLD  ←──────────────────────  (rejoins anywhere pre-offer)
             ↓
   REJECTED / WITHDRAWN  ← reachable from every live status.  All three are TERMINAL.
```

**Drive the status dropdown from `allowedNextStatuses` on the response, never from the
full enum.** The transitions are a constrained graph; offering an illegal one just
produces a 422 the user can't act on. Four rules worth knowing:

1. **`APPLIED → JOINED` is refused.** No skipping screening and interviews — Phase 6
   converts a `JOINED` application into a worker and Phase 10 must show they were vetted.
2. **Nothing leaves `JOINED`, `REJECTED` or `WITHDRAWN`.** `allowedNextStatuses` is `[]`
   and `terminal` is `true`. Re-engaging a rejected candidate is a *new* application.
3. **A `note` is required to reject** — 422 without one. It's all the next recruiter has.
4. **`INTERVIEW_SCHEDULED` cannot be set by hand.** Scheduling an interview sets it, so
   the status can't claim a meeting that doesn't exist. Manual attempts are 422.

`ON_HOLD` is the one status that exists to be left and returned from — "good candidate, no
opening right now". Label it that way; `candidateFacingLabel` renders it as "Kept on file
for a future opening", not as a rejection.

`GET /applications/{id}/detail` is one call for the drawer:

```json
{ "application": { ... }, "history": [ ... ], "interviews": [ ... ] }
```

The `history` is oldest-first and its **first entry has a null `fromStatus`** — that's the
submission, not a move. `madeByStaff` is `false` when the candidate acted on their own
application (a public submission or a self-service withdrawal) and `true` for staff moves.
Render that distinction; it's what makes the trail auditable.

`GET /jobs/{jobId}/pipeline` drives the board columns:

```json
{ "jobId": 1, "openings": 3, "openingsCommitted": 1, "openingsRemaining": 2,
  "totalApplications": 7,
  "countsByStatus": { "APPLIED": 4, "SCREENING": 0, ..., "WITHDRAWN": 1 } }
```

**Every status is present, including the zeros**, so you can render a complete set of
columns without inventing missing keys.

`POST /jobs/{jobId}/applications` is for walk-ins and agency CVs. The candidate **must
already exist** — so the form needs a candidate picker, not name fields. Create them with
`POST /candidates` first.

#### B13 · Interviews — `APPLICATION_READ` / `APPLICATION_MANAGE`

| Action | CRUD | Endpoint | Permission |
|---|---|---|---|
| List / diary | **R** | `GET /interviews` | `APPLICATION_READ` |
| Per application, by round | **R** | `GET /applications/{id}/interviews` | `APPLICATION_READ` |
| Read | **R** | `GET /interviews/{interviewId}` | `APPLICATION_READ` |
| Schedule | **C** | `POST /applications/{id}/interviews` | `APPLICATION_MANAGE` |
| Reschedule | **U** | `PUT /interviews/{interviewId}` | `APPLICATION_MANAGE` |
| Record outcome | **U** | `PATCH /interviews/{interviewId}/result` | `APPLICATION_MANAGE` |
| Cancel | **D** | `DELETE /interviews/{interviewId}` | `APPLICATION_MANAGE` |

Filters: `interviewerUserId`, `result`, `from`, `to`, **`awaitingFeedback=true`**.

**`awaitingFeedback=true` is the screen that matters** — slots that have passed with no
outcome recorded. An interview nobody wrote up is a candidate left waiting, which is how
good applicants are lost. Put it on the recruiter's home page.

Scheduling (`InterviewRequest`): `scheduledAt*` (ISO-8601 UTC, **must be future**),
`durationMinutes` (default 30), `mode*`, `venueOrLink`, `interviewerUserId`,
`roundNumber`.

- **Omit `roundNumber`** to append the next round. Passing one that already exists is 422.
- **`venueOrLink` is required for `IN_PERSON` and `VIDEO`** — a candidate can't attend an
  interview with no location. `PHONE` is exempt (their number is on the record). Change
  the field's label and validation with the mode.
- The application must be at `SCREENING` or `SHORTLISTED`; anything else is 422.

**Scheduling moves the application to `INTERVIEW_SCHEDULED` for you.** Don't also patch
the status. And **cancelling the last outstanding slot walks it back to `SHORTLISTED`** —
so a cancel may change the application's status too. Refetch it.

Recording the outcome (`InterviewResultRequest`): `result*` (`PASS`/`FAIL`/`NO_SHOW`),
`rating` (1–5), `feedback`, `advanceApplication`.

- **Write-once.** A recorded outcome cannot be revised — it's the evidence the candidate
  was assessed. Schedule another round instead. Second attempts are 422. Disable the form
  once `result !== 'PENDING'`.
- **`advanceApplication: true`** moves a passed application to `SELECTED` in the same
  call. Not the default: passing round one of three isn't a decision to hire, and
  `SELECTED` commits an opening. Offer it as a checkbox on the pass path.
- **`FAIL` and `NO_SHOW` do not auto-reject.** The application stays where it is.
  Rejecting needs a reason and is a person's decision; `ON_HOLD` exists for a candidate
  who suits another role. Prompt for the next step after recording a failure.
- `NO_SHOW` is tracked separately from `FAIL` because it measures the confirmation
  process, not the candidate. Report on it.
- The outcome can be recorded up to **2 hours before** the scheduled time, so an interview
  that finished early can be written up. Beyond that it's 422 — almost always the wrong
  row.

Cancelling is refused with **422** once a result is recorded.

---

### Panel C · Client portal (CLIENT role)

The client's own users signing in to see their account. Everything is scoped
server-side — a client can only ever reach their own data, enforced in three layers
(authentication → permission → ownership).

| Screen | CRUD | Endpoint |
|---|---|---|
| **Home / my company** | **R** | `GET /clients/me` |
| My sites | **R** | `GET /clients/{myId}/sites`, `GET /sites/{siteId}` |
| My contracts | **R** | `GET /clients/{myId}/contracts`, `GET /contracts/{id}` |
| My rate cards | **R** | `GET /clients/{myId}/rate-cards` |
| Resolve a rate | **R** | `GET /clients/{myId}/rate-cards/resolve` |
| My profile / password | **U** | `POST /auth/change-password` |
| My sessions | **R/D** | `GET /auth/sessions`, `DELETE /auth/sessions/{id}` |

**Read-only.** `CLIENT` holds `CLIENT_READ` but not `CLIENT_WRITE`, so there are no
create or edit screens in this panel. Don't render Save buttons.

**Start every session with `GET /clients/me`** to learn the client's own id, then use it
in the nested URLs. Cache it for the session.

Isolation is verified by integration test and live probe:

| Request | Result |
|---|---|
| own client id | `200` |
| another client's id | **`404`** — never 403, so ids aren't confirmed |
| `?clientId=<someone else>` on a list | overridden server-side; you get your own data |

So you **cannot** widen scope from the client, and you should not try.

Two edge cases to handle, and note they return **different** codes for the same
underlying condition — verified live:

| Request | When the user has no linked client | Code |
|---|---|---|
| `GET /clients` | "Your account is not linked to a client company. Please contact support." | **404** |
| `GET /clients/me` | "Your account is not linked to a client company." | **422** |

This happens if a CLIENT-role user was created without being linked, or had their link
deactivated. Catch both and show a support message rather than an error page.

See the commercial note in **B3.7** before deciding whether to show
`grossMarginPerWorker` here.

---

### Panel D · Candidate portal (CANDIDATE role)

Real as of Phase 5. Everything is scoped to the caller — there are no ids to widen.

| Screen | CRUD | Endpoint | Permission |
|---|---|---|---|
| My profile | **R** | `GET /me/candidate-profile` | `SELF_PROFILE_MANAGE` |
| Edit my profile | **U** | `PUT /me/candidate-profile` | `SELF_PROFILE_MANAGE` |
| Upload my CV | **U** | `POST /me/resume` | `SELF_PROFILE_MANAGE` |
| My applications | **R** | `GET /me/applications` | `SELF_APPLICATION_MANAGE` |
| One application | **R** | `GET /me/applications/{id}` | `SELF_APPLICATION_MANAGE` |
| Apply for a job | **C** | `POST /me/applications/jobs/{jobId}` | `SELF_APPLICATION_MANAGE` |
| Withdraw | **U** | `PATCH /me/applications/{id}/withdraw` | `SELF_APPLICATION_MANAGE` |
| Change password | **U** | `POST /auth/change-password` | authenticated |
| My sessions | **R/D** | `GET /auth/sessions`, `DELETE /auth/sessions/{id}` | authenticated |

**Browse vacancies through the anonymous endpoints** — `GET /public/jobs`. A candidate has
no permission for the staff job endpoints, by design (§14). Don't send the token there; it
returns 403.

**`GET /me/candidate-profile` returns 422 for a brand-new account.** The profile is
created by the first write, so a freshly registered candidate has none. Treat the 422 as
"empty profile, show the form" rather than an error. `PUT /me/candidate-profile` and
`POST /me/resume` both create it if absent.

**Your email is not editable** — it identifies you across every application. `SelfRequest`
has no email field, and `consentGiven` must be `true` (withdrawing consent is a deletion
request, not a profile update).

**Applying uses the CV on your profile as it stands**, snapshotted against that
application. So prompt for a CV upload before the first apply, and make clear that later
CV changes don't alter past applications.

What a candidate deliberately does **not** see — these fields are absent from the
response, not blanked:

| Hidden | Why |
|---|---|
| `feedback`, `rating` | the panel's write-up is not theirs to read |
| `assignedToUserId`, `assignedToName` | which recruiter owns them is internal |
| `rejectionReason` | the internal wording; `statusLabel` is what they get |
| `notes`, `spamSuspected`, `source` | internal candidate fields |

Use **`statusLabel`**, not `status`, in the UI. It renders `SCREENING` as "Under review"
and `ON_HOLD` as "Kept on file for a future opening" — the raw enum reads as a rejection.

`withdrawable` tells you whether to show the button. Withdrawing is **terminal**: a second
attempt is 422, and re-applying to the same vacancy is impossible. Confirm before calling.

Someone else's application id returns **404**, not 403.

**A guest application is picked up automatically.** Someone who applied without an account
and later registers with the same email sees that application in their portal on first
load — one candidate record, not two.

### Panel D (cont.) · Worker

Still **authentication only**. `SELF_PAYSLIP_READ`, `SELF_ATTENDANCE_READ` and
`SELF_LEAVE_MANAGE` are seeded and enforced, but the endpoints behind them are Phase 7.
Build the shell and the auth flows; leave the feature routes as placeholders.

---

## 6. Endpoint reference

All 121 operations, grouped, with the permission each requires.

**Public — no authentication**

| Method | Path |
|---|---|
| `GET` | `/api/v1/public/company-profile` |
| `GET` | `/api/v1/public/services` |
| `GET` | `/api/v1/public/services/{slug}` |
| `GET` | `/api/v1/public/industries` |
| `GET` | `/api/v1/public/testimonials` |
| `GET` | `/api/v1/public/manpower-categories` |
| `POST` | `/api/v1/public/enquiries` |
| `POST` | `/api/v1/public/contact-messages` |
| `GET` | `/api/v1/public/files/**` |
| `GET` | `/robots.txt`, `/sitemap.xml` |

**Auth — no authentication**

`POST /auth/register/candidate`, `/auth/register/client`, `/auth/login`, `/auth/refresh`,
`/auth/verify-email`, `/auth/resend-verification`, `/auth/forgot-password`,
`/auth/reset-password`

**Auth — authenticated**

`GET /auth/me` · `POST /auth/logout` · `POST /auth/logout-all` ·
`POST /auth/change-password` · `GET /auth/sessions` · `DELETE /auth/sessions/{sessionId}`

**Settings**

| Method | Path | Permission |
|---|---|---|
| `GET` | `/settings/company` | authenticated |
| `PUT` | `/settings/company` | `SETTINGS_MANAGE` |
| `POST` | `/settings/company/logo` | `SETTINGS_MANAGE` |

**Website content — all `CONTENT_MANAGE`**

`GET/POST /services` · `GET/PUT/DELETE /services/{id}` · `PATCH /services/{id}/publish` ·
`POST /services/{id}/hero-image` · `GET/POST /industries` ·
`PUT/DELETE /industries/{id}` · `GET/POST /testimonials` ·
`PUT/DELETE /testimonials/{id}` · `PATCH /testimonials/{id}/publish` ·
`POST /testimonials/{id}/image`

**Master data**

| Method | Path | Permission |
|---|---|---|
| `GET` | `/manpower-categories`, `/manpower-categories/{id}` | authenticated |
| `POST` | `/manpower-categories` | `CONTENT_MANAGE` |
| `PUT` | `/manpower-categories/{id}` | `CONTENT_MANAGE` |
| `PATCH` | `/manpower-categories/{id}/activate`, `/deactivate` | `CONTENT_MANAGE` |
| `GET` | `/skills` | authenticated |
| `POST` | `/skills` | `CONTENT_MANAGE` |
| `PUT` | `/skills/{skillId}` | `CONTENT_MANAGE` |
| `PATCH` | `/skills/{skillId}/active` | `CONTENT_MANAGE` |
| `GET` | `/manpower-categories/{categoryId}/skills` | authenticated |
| `PUT` | `/manpower-categories/{categoryId}/skills` | `CONTENT_MANAGE` |

**Enquiries & contact messages — all `ENQUIRY_MANAGE`**

`GET /enquiries` · `GET /enquiries/{id}` · `PATCH /enquiries/{id}/status` ·
`PATCH /enquiries/{id}/assign` · `DELETE /enquiries/{id}` · `GET /contact-messages` ·
`GET /contact-messages/unread-count` · `GET /contact-messages/{id}` ·
`PATCH /contact-messages/{id}/read` · `/replied` · `/spam` ·
`DELETE /contact-messages/{id}`

**Clients, sites, contracts, rate cards**

`CLIENT_READ` for every `GET`; `CLIENT_WRITE` for every write; `CLIENT_APPROVE` for
`POST /clients/approve-registration/{userId}`.

| Method | Path |
|---|---|
| `GET`/`POST` | `/clients` |
| `GET` | `/clients/me` |
| `GET`/`PUT`/`DELETE` | `/clients/{clientId}` |
| `GET` | `/clients/{clientId}/detail` |
| `PATCH` | `/clients/{clientId}/status` |
| `GET`/`POST` | `/clients/{clientId}/users` |
| `PATCH` | `/clients/{clientId}/users/{clientUserId}/primary-contact`, `/active` |
| `POST` | `/clients/approve-registration/{userId}` |
| `GET`/`POST` | `/clients/{clientId}/sites` |
| `GET`/`PUT`/`DELETE` | `/sites/{siteId}` |
| `PATCH` | `/sites/{siteId}/active` |
| `GET`/`POST` | `/clients/{clientId}/contracts` |
| `GET` | `/clients/{clientId}/contracts/in-force` |
| `GET`/`PUT`/`DELETE` | `/contracts/{contractId}` |
| `PATCH` | `/contracts/{contractId}/activate`, `/terminate` |
| `POST` | `/contracts/{contractId}/document` |
| `GET`/`POST` | `/clients/{clientId}/rate-cards` |
| `GET` | `/clients/{clientId}/rate-cards/resolve` |
| `GET`/`PUT`/`DELETE` | `/rate-cards/{rateCardId}` |

**Users & roles**

| Method | Path | Permission |
|---|---|---|
| `GET` | `/users`, `/users/{userId}` | `USER_READ` |
| `POST` | `/users` | `USER_MANAGE` |
| `PUT` | `/users/{userId}` | `USER_MANAGE` |
| `PATCH` | `/users/{userId}/disable`, `/enable`, `/unlock` | `USER_MANAGE` |
| `PUT` | `/users/{userId}/roles` | `ROLE_MANAGE` |
| `PATCH` | `/users/{userId}/approve-client`, `/reject-client` | `CLIENT_APPROVE` |
| `GET` | `/users/permissions-check` | authenticated |
| `GET` | `/roles`, `/roles/{roleId}`, `/roles/permissions` | `USER_READ` |
| `PUT` | `/roles/{roleId}/permissions` | `ROLE_MANAGE` |

**Public careers — no authentication**

| Method | Path |
|---|---|
| `GET` | `/api/v1/public/jobs` |
| `GET` | `/api/v1/public/jobs/{slug}` |
| `POST` | `/api/v1/public/jobs/{slug}/apply` |

**Jobs**

| Method | Path | Permission |
|---|---|---|
| `GET` | `/jobs`, `/jobs/{jobId}` | `JOB_READ` |
| `POST` | `/jobs` | `JOB_MANAGE` |
| `PUT` | `/jobs/{jobId}` | `JOB_MANAGE` |
| `PATCH` | `/jobs/{jobId}/status` | `JOB_MANAGE` |
| `DELETE` | `/jobs/{jobId}` | `JOB_MANAGE` |
| `GET` | `/jobs/{jobId}/pipeline` | `APPLICATION_READ` |
| `POST` | `/jobs/{jobId}/applications` | `APPLICATION_MANAGE` |

**Candidates**

| Method | Path | Permission |
|---|---|---|
| `GET` | `/candidates`, `/candidates/{candidateId}` | `CANDIDATE_READ` |
| `POST` | `/candidates` | `CANDIDATE_WRITE` |
| `PUT` | `/candidates/{candidateId}` | `CANDIDATE_WRITE` |
| `POST` | `/candidates/{candidateId}/resume` | `CANDIDATE_WRITE` |
| `PATCH` | `/candidates/{candidateId}/spam` | `CANDIDATE_WRITE` |
| `DELETE` | `/candidates/{candidateId}` | `CANDIDATE_WRITE` |

**Applications**

| Method | Path | Permission |
|---|---|---|
| `GET` | `/applications`, `/applications/{id}`, `/applications/{id}/detail` | `APPLICATION_READ` |
| `PATCH` | `/applications/{id}/status`, `/applications/{id}/assign` | `APPLICATION_MANAGE` |

**Interviews**

| Method | Path | Permission |
|---|---|---|
| `GET` | `/interviews`, `/interviews/{id}`, `/applications/{id}/interviews` | `APPLICATION_READ` |
| `POST` | `/applications/{id}/interviews` | `APPLICATION_MANAGE` |
| `PUT` | `/interviews/{interviewId}` | `APPLICATION_MANAGE` |
| `PATCH` | `/interviews/{interviewId}/result` | `APPLICATION_MANAGE` |
| `DELETE` | `/interviews/{interviewId}` | `APPLICATION_MANAGE` |

**Candidate portal**

| Method | Path | Permission |
|---|---|---|
| `GET`/`PUT` | `/me/candidate-profile` | `SELF_PROFILE_MANAGE` |
| `POST` | `/me/resume` | `SELF_PROFILE_MANAGE` |
| `GET` | `/me/applications`, `/me/applications/{id}` | `SELF_APPLICATION_MANAGE` |
| `POST` | `/me/applications/jobs/{jobId}` | `SELF_APPLICATION_MANAGE` |
| `PATCH` | `/me/applications/{id}/withdraw` | `SELF_APPLICATION_MANAGE` |

**Documents & audit**

| Method | Path | Permission |
|---|---|---|
| `GET` | `/documents/{id}` | authenticated |
| `GET` | `/documents/{id}/download` | authenticated |
| `GET` | `/audit-logs` | `AUDIT_VIEW` |

---

## 7. Pagination, sorting, filtering

Paged endpoints take `page` (0-based), `size`, `sort` (`field,asc|desc`, repeatable):

```
GET /api/v1/clients?page=0&size=20&sort=legalName,asc&status=ACTIVE&stateCode=27
```

Every paged response uses the same envelope:

```json
{ "content": [...], "page": 0, "size": 20, "totalElements": 134, "totalPages": 7,
  "first": true, "last": false, "empty": false }
```

**Paged:** `/clients`, `/users`, `/enquiries`, `/contact-messages`, `/testimonials`,
`/audit-logs`, `/jobs`, `/candidates`, `/applications`, `/interviews`,
`/public/jobs`.

**Not paged — plain arrays:** `/skills`, `/manpower-categories`, `/services`,
`/industries`, the other `/public/*` lists, the client-nested lists (`/sites`,
`/contracts`, `/rate-cards`), `/me/applications`, and
`/applications/{id}/interviews`. Don't send `page`/`size` there; it won't error, it just
won't do anything.

Note `/public/jobs` **is** paged, unlike the rest of `/public/*` — a job board needs it.

Filters are AND-ed, and omitted means "no constraint". `search` is a case-insensitive
substring match across the main text fields.

**Null fields are omitted from responses, not sent as `null`.** So read defensively —
`response.effectiveTo` is `undefined`, not `null`, on a current rate card. Optional
chaining everywhere.

---

## 8. Enums for dropdowns

Hardcoding these is fine; they're compile-time constants in the backend, not data.

| Enum | Values | Used by |
|---|---|---|
| `UserStatus` | `PENDING_VERIFICATION`, `PENDING_APPROVAL`, `ACTIVE`, `DISABLED`, `REJECTED` | users |
| `RoleName` | `SUPER_ADMIN`, `ADMIN`, `HR_RECRUITER`, `OPERATIONS_MANAGER`, `SITE_SUPERVISOR`, `ACCOUNTS`, `CLIENT`, `WORKER`, `CANDIDATE` | users, roles |
| `ClientStatus` | `PENDING_APPROVAL`, `ACTIVE`, `SUSPENDED`, `INACTIVE`, `REJECTED` | clients |
| `GstTreatment` | `INTRA_STATE`, `INTER_STATE`, `UNKNOWN` | clients (read-only) |
| `ContractStatus` | `DRAFT`, `ACTIVE`, `EXPIRED`, `TERMINATED` | contracts |
| `ServiceChargeType` | `PERCENTAGE`, `FIXED_PER_WORKER` | contracts |
| `SkillLevel` | `UNSKILLED`, `SEMI_SKILLED`, `SKILLED`, `HIGHLY_SKILLED` | categories, rate cards |
| `EnquiryStatus` | `NEW`, `CONTACTED`, `QUALIFIED`, `CONVERTED`, `CLOSED`, `SPAM` | enquiries |
| `DocumentOwnerType` | `COMPANY_LOGO`, `TESTIMONIAL`, `SERVICE_IMAGE`, `WORKER`, `CLIENT`, `CONTRACT`, `LICENCE`, `CANDIDATE`, `CHALLAN` | documents |
| `JobStatus` | `DRAFT`, `OPEN`, `ON_HOLD`, `CLOSED`, `FILLED` | jobs |
| `Shift` | `DAY`, `NIGHT`, `ROTATIONAL` | jobs |
| `ApplicationStatus` | `APPLIED`, `SCREENING`, `SHORTLISTED`, `INTERVIEW_SCHEDULED`, `SELECTED`, `OFFERED`, `JOINED`, `REJECTED`, `ON_HOLD`, `WITHDRAWN` | applications |
| `CandidateSource` | `WEBSITE`, `REFERRAL`, `WALK_IN`, `AGENCY`, `CAMPUS`, `OTHER` | candidates, applications |
| `InterviewMode` | `IN_PERSON`, `PHONE`, `VIDEO` | interviews |
| `InterviewResult` | `PENDING`, `PASS`, `FAIL`, `NO_SHOW` | interviews |

> **`ApplicationStatus` is the one enum you must not treat as a flat list.** Transitions
> are constrained — read `allowedNextStatuses` off the application instead. See Panel B12.

Keep a single label map; don't show raw `SEMI_SKILLED` to users. Where the API already
provides a label (`skillLevelLabel`, `serviceChargeLabel`, `attribution`,
`displayLabel`, `displayName`), prefer it.

---

## 9. Validation rules (Indian formats)

Mirror these client-side for instant feedback, but **never rely on them** — the backend
validates independently and is the authority.

| Field | Rule | Example |
|---|---|---|
| Mobile | `^[6-9][0-9]{9}$` — 10 digits, starts 6–9, **no `+91`, no spaces** | `9876543210` |
| GSTIN | 15 chars, `^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$` | `27AABCU9603R1ZM` |
| PAN | `^[A-Z]{5}[0-9]{4}[A-Z]$` | `AABCS1234A` |
| TAN | `^[A-Z]{4}[0-9]{5}[A-Z]$` | `PUNA12345B` |
| CIN | 21 chars, `^[LU][0-9]{5}[A-Z]{2}[0-9]{4}[A-Z]{3}[0-9]{6}$` | `U72200MH2010PTC123456` |
| PIN code | `^[1-9][0-9]{5}$` — 6 digits, **no leading zero** | `411019` |
| GST state code | `^[0-3][0-9]$` — two digits | `27` (Maharashtra) |
| IFSC | `^[A-Z]{4}0[A-Z0-9]{6}$` — 5th char is **zero** | `HDFC0001234` |
| Bank account | `^[0-9]{9,18}$` | |
| Landline/phone | `^[0-9+()\-\s]{7,20}$` | `+91 20 2345 6789` |
| Slug | `^[a-z0-9]+(?:-[a-z0-9]+)*$` — lowercase kebab | `payroll-outsourcing` |
| Site code | `^[A-Za-z0-9][A-Za-z0-9_\-]{1,39}$` — **stored upper-case** | `PUNE-PLANT-1` |
| Category code | letters/digits/underscore — **stored upper-case** | `SECURITY_GUARD` |

**Uppercase on submit, don't fight the user.** GSTIN, PAN, TAN, CIN and IFSC are all
uppercase-only patterns. Upper-case the value as it's typed rather than rejecting
`aabcs1234a`.

**Every optional pattern field accepts empty string** (`^$|^...$`). "Not known yet" is
valid; don't mark them required.

Passwords (`@StrongPassword`): minimum 8 characters with upper, lower, digit and special.
Mirror the rule and show a live checklist — a server round-trip for a weak password is a
poor experience.

---

## 10. File uploads and downloads

All uploads are `multipart/form-data` with the part named **`file`**.

| Endpoint | What | Part name |
|---|---|---|
| `POST /settings/company/logo` | company logo (image) | `file` |
| `POST /services/{id}/hero-image` | service hero image | `file` |
| `POST /testimonials/{id}/image` | client logo or person's photo | `file` |
| `POST /contracts/{contractId}/document` | signed contract (PDF) | `file` |
| `POST /candidates/{candidateId}/resume` | candidate CV (**PDF only**) | `file` |
| `POST /me/resume` | your own CV (**PDF only**) | `file` |
| `POST /public/jobs/{slug}/apply` | application + optional CV | `application` + `resume` |

> **The apply endpoint is the exception**: two parts, named `application` (JSON) and
> `resume` (PDF), not one named `file`. See Panel A.

| Limit | Value |
|---|---|
| Images | **2 MB** |
| Other files | **10 MB** |
| Whole request | 25 MB |

```ts
const body = new FormData();
body.append('file', file);                 // the part MUST be named "file"
await fetch(`/api/v1/services/${id}/hero-image`, {
  method: 'POST', body, credentials: 'include',
  headers: { Authorization: `Bearer ${token}` },   // do NOT set Content-Type
});
```

Never set `Content-Type` yourself on a `FormData` request — the browser must add the
multipart boundary.

**Type is detected from file content (magic bytes), not the extension.** An HTML file
renamed to `.png` is rejected. Validate `file.type` client-side for a fast message, but
expect the server to be stricter.

**CVs are PDF only.** A `.doc` is an OLE2 container that can carry macros, so a Word
document renamed to `.pdf` is refused here rather than opened by whoever downloads it.
Say "PDF only" on the file input; "any document" will generate support tickets.

Over-size gives `413 PAYLOAD_TOO_LARGE`; wrong type gives `415` or `400`.

### Two kinds of file URL

**Public assets** — logos, hero images, testimonial photos. Served without
authentication from `GET /api/v1/public/files/**`. Responses already contain the complete
path (`logoUrl`, `heroImageUrl`, `photoUrl`); drop it straight into `src`:

```jsx
<img src={service.heroImageUrl} alt={service.title} />
```

Don't construct these by hand. A private key requested through the public route returns
`404`.

> Note the admin DTOs expose `logoPath` / `heroImagePath` / `photoPath` (raw stored
> paths) while the public DTOs expose `logoUrl` / `heroImageUrl` / `photoUrl` (ready to
> use). In the CMS, read the public endpoint for previews.

**Private documents** — contracts and anything sensitive. Require authentication:

| Method | Path | Returns |
|---|---|---|
| `GET` | `/documents/{id}` | metadata — `originalFileName`, `contentType`, `sizeBytes`, `image`, `publicAsset`, `downloadUrl` |
| `GET` | `/documents/{id}/download` | the bytes |

Because the download needs an `Authorization` header, a plain `<a href>` won't work.
Fetch as a blob and trigger the save:

```ts
const res = await fetch(`/api/v1/documents/${id}/download`,
  { headers: { Authorization: `Bearer ${token}` }, credentials: 'include' });
const url = URL.createObjectURL(await res.blob());
const a = Object.assign(document.createElement('a'), { href: url, download: fileName });
a.click();
URL.revokeObjectURL(url);
```

---

## 11. Permission → screen matrix

Drive the whole UI from `user.permissions`. A single helper is enough:

```ts
const can = (p: string) => user.permissions.includes(p);

{can('CLIENT_WRITE') && <Button onClick={addClient}>Add client</Button>}
```

| Permission | Unlocks |
|---|---|
| `SETTINGS_MANAGE` | Company settings (edit) |
| `ROLE_MANAGE` | Roles & permissions grid; user role editing |
| `USER_READ` | Users list, Roles list (read-only) |
| `USER_MANAGE` | Create/edit/disable/enable/unlock users |
| `CONTENT_MANAGE` | Website CMS; master-data writes |
| `ENQUIRY_MANAGE` | Enquiries board; contact inbox |
| `CLIENT_READ` | Clients, sites, contracts, rate cards — **read** |
| `CLIENT_WRITE` | …**write** |
| `CLIENT_APPROVE` | Client registration approvals |
| `AUDIT_VIEW` | Audit trail |
| `JOB_READ` | Jobs list and detail — **staff only**, names the client |
| `JOB_MANAGE` | Create/edit/publish/delete job adverts |
| `CANDIDATE_READ` | Candidates list and detail |
| `CANDIDATE_WRITE` | Create/edit candidates, upload CVs, spam flag |
| `APPLICATION_READ` | Pipeline board, application detail, interview diary |
| `APPLICATION_MANAGE` | Move the funnel, assign, schedule and write up interviews |
| `SELF_PROFILE_MANAGE` | Candidate portal — own profile and CV |
| `SELF_APPLICATION_MANAGE` | Candidate portal — own applications, apply, withdraw |
| *(authenticated)* | `GET /settings/company`, master-data reads, documents, own sessions |

Permissions seeded but with **no endpoints yet** (Phases 6–11) — don't build menus for
these: `ATTENDANCE_*`, `LEAVE_APPROVE`, `DEPLOYMENT_*`, `WORKER_*`, `REQUISITION_*`,
`PAYROLL_*`, `INVOICE_*`, `PAYMENT_MANAGE`, `COMPLIANCE_*`, `REPORT_VIEW`,
`SELF_PAYSLIP_READ`, `SELF_ATTENDANCE_READ`, `SELF_LEAVE_MANAGE`.

**Hiding a control is cosmetic.** The backend enforces every rule independently; a hidden
button that gets called anyway returns `403`. Hide for clarity, not for security.

---

## 12. Not built yet

Phases 1–4 are complete. These exist as **permissions and roles only** — no endpoints.
Don't design screens against them yet.

| Phase | Scope | Affected panel |
|---|---|---|
| 6 | Workers, encrypted documents, deployment, requisition workflow | Staff (OPS), Worker |
| 7 | Attendance & leave — supervisor entry, Excel upload, client approval | Staff (SUPERVISOR), Client, Worker |
| 8 | Payroll — statutory engine, payslip PDF, bank/PF/ESI exports | Staff (ACCOUNTS) |
| 9 | Billing & invoicing — GST invoices, payments, ageing | Staff (ACCOUNTS), Client |
| 10 | Compliance, reminders, notifications, dashboards, reports | Staff (ACCOUNTS) |
| 11 | Hardening — TOTP 2FA, CI pipeline | all |

What this means in practice today:

- **SITE_SUPERVISOR and WORKER have no feature screens.** Ship the auth shell.
- **CANDIDATE is now a real panel** — profile, CV, applications, withdraw. See Panel D.
- **The client portal is read-only and thin** — company, sites, contracts, rates. Invoices
  and deployed-worker lists come in Phases 9 and 6. Requisitions ("I need 20 guards at
  this site") are Phase 6, which is where `REQUISITION_CREATE` finally does something.
- **There is no dashboard API.** Build the staff landing page from what exists:
  `GET /contact-messages/unread-count`, `GET /enquiries?status=NEW` (read `totalElements`),
  `GET /clients?status=ACTIVE`, `GET /users?status=PENDING_APPROVAL`,
  `GET /jobs?status=OPEN`, `GET /applications?unassigned=true`, and
  `GET /interviews?awaitingFeedback=true` — the last two are the queues that most need
  attention.

---

## 13. Gotchas (things that will bite you)

1. **Serialise token refresh.** Two concurrent refreshes trigger replay detection and
   revoke *every* session for that user. One shared in-flight promise. (§3.2)
2. **`credentials: 'include'` on every request**, or the refresh cookie is never stored
   and sessions die after 15 minutes.
3. **Add your dev-server origin to CORS**, or every call fails preflight with `403`
   before reaching a controller. (§1)
4. **Null fields are omitted, not `null`.** `rateCard.effectiveTo` is `undefined` when
   current. Optional-chain everything.
5. **Nested-create, top-level-edit.** `POST /clients/{id}/sites` but
   `PUT /sites/{siteId}`. Same for contracts and rate cards.
6. **`PUT` on role permissions, user roles and category skills is a full replacement.**
   Send the complete set; omissions are deletions. Roles and permissions are sent as
   **names** (`{"roles":["ADMIN"]}`, `{"permissions":["CLIENT_READ"]}`); category skills
   as **ids** (`{"skillIds":[1,4,7]}`). Don't assume ids everywhere.
7. **Rate cards and categories are never edited in place.** Supersede a live rate card;
   deactivate a category. `PUT`/`DELETE` on a live rate card returns `422`.
8. **`422` is a business rule, not a field error.** Show it as a dialog, not inline.
9. **`404` can mean "not yours".** Don't distinguish it from genuine absence.
10. **Codes get upper-cased on save** (`siteCode`, category `code`). Echo the stored
    value back so it doesn't look like a bug.
11. **`GET /clients` → 404 but `GET /clients/me` → 422** for an unlinked CLIENT user.
    Handle both. (Panel C)
12. **Role changes invalidate tokens.** Call `GET /auth/me` and re-render the menu after
    editing the current user's roles.
13. **Honeypot fields must be rendered and left empty**, and spam is accepted with `201`,
    never rejected. Don't tell the submitter. (Panel A)
14. **Money is JSON numbers** (`18000.0`). For display, format from the string form;
    don't do currency arithmetic in floating point. Dates are `YYYY-MM-DD`; timestamps
    are ISO-8601 UTC (`2026-10-10T04:47:07.768486Z`) — convert to IST for display.
15. **`forgot-password` always returns 200.** Never imply the address was found.
16. **Drive the application status dropdown from `allowedNextStatuses`**, not the enum.
    The funnel is a constrained graph; `APPLIED → JOINED` and anything out of a terminal
    state are 422.
17. **Rejecting an application requires a `note`.** 422 without one.
18. **Don't set `INTERVIEW_SCHEDULED` by hand** — schedule the interview, which sets it.
    And scheduling or cancelling an interview changes the application's status as a side
    effect, so refetch it afterwards.
19. **Interview outcomes are write-once.** Disable the form once `result !== 'PENDING'`.
    A `FAIL` does *not* auto-reject the application.
20. **The apply endpoint is multipart with parts named `application` and `resume`** — not
    `file`, and not JSON. CVs are PDF only, checked by content.
21. **A candidate's email is immutable** everywhere — `PUT /candidates/{id}` with a
    different address is 422, and the self-profile DTO has no email field at all.
22. **`GET /me/candidate-profile` is 422 for a new account.** That means "no profile yet,
    show the form", not an error. The first `PUT` creates it.
23. **Job adverts are always created as `DRAFT`.** Publishing is a second call.
24. **`openingsRemaining` can be negative** when a job is over-committed. Don't assume
    it's ≥ 0.

---

## 14. What changed in Phase 5

If you built against the Phase 1–4 version of this document, these are the breaking and
near-breaking changes.

### `CANDIDATE` lost `JOB_READ` — one genuine break

A candidate token used to get `200` from `GET /api/v1/jobs`. It now gets **403**.

That endpoint is the *staff* job list, and it returns the client each vacancy was raised
for, unannounced drafts, and how many people have applied — none of which an applicant
should see. The V2 seed granted `JOB_READ` to candidates on the reasoning that "job
adverts are public anyway", which is true of the anonymous careers endpoints and not of
this one. Migration **V7** revokes it.

**If your candidate UI called `/api/v1/jobs`, point it at `/api/v1/public/jobs`** — which
needs no token at all.

### Additions, nothing else removed

| Area | What |
|---|---|
| Public careers | `GET /public/jobs`, `GET /public/jobs/{slug}`, `POST /public/jobs/{slug}/apply` |
| Jobs | 8 operations under `/jobs` |
| Candidates | 7 operations under `/candidates` |
| Applications | 5 operations under `/applications`, plus `POST /jobs/{id}/applications` |
| Interviews | 7 operations under `/interviews` and `/applications/{id}/interviews` |
| Candidate portal | 7 operations under `/me` |

### Behaviour worth re-reading

- `/public/jobs` **is paged**, unlike the other `/public/*` list endpoints.
- `POST /public/jobs/{slug}/apply` is the only anonymous multipart endpoint, and its parts
  are `application` and `resume` rather than `file`.
- Scheduling and cancelling an interview **mutate the application's status**. Refetch.
- `ApplicationStatus` transitions are constrained. Read `allowedNextStatuses`.

---

## Appendix · State of the backend

| | |
|---|---|
| Phases complete | 1–5 of 11 |
| Endpoints | 158 operations over 119 paths |
| OpenAPI schemas | 102, all explicitly named |
| Tests | **301 unit + 130 integration = 431, all passing** |
| Flyway migrations | V1–V7 |
| Stack | Java 21 · Spring Boot 4.1.1 · Spring Security 7.1.1 · MySQL 8 · Flyway · MapStruct |

Companion documents: `docs/decisions.md` (why things are the way they are),
`docs/rbac.md` (the full role/permission matrix), `docs/er-diagram.md` (data model).
