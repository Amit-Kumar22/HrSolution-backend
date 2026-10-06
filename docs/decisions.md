# Architecture decisions

Why things are the way they are. Short entries, newest concerns first. If you
change one of these, update the entry rather than leaving it stale.

---

## 1. Maven at the repository root, not Gradle in `/backend`

The project arrived as a Gradle build at the repository root. The brief asked for
Maven in `/backend` alongside a `/frontend`. Since the frontend was dropped and
this is a backend-only repository, a `/backend` subdirectory would add a nesting
level containing everything — so the Maven project sits at the root, matching
`ems-office-project`.

Converting Gradle → Maven meant re-deriving the Boot 4 starter coordinates.
Boot 4 renamed things:

| Boot 3 | Boot 4 |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` (one aggregate) | split per module: `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test` |
| Flyway auto-configured via `flyway-core` | `spring-boot-starter-flyway` |

The module `-test` starters each pull in `spring-boot-starter-test` transitively,
so JUnit, Mockito and AssertJ still arrive.

---

## 2. Library versions verified against the Boot 4.1.1 BOM

The brief was written against Boot 3 assumptions. What Boot 4.1.1 actually
manages:

| Library | Version | Note |
|---|---|---|
| Spring Framework | 7.0.9 | |
| Spring Security | **7.1.1** | brief said 6 |
| Hibernate | 7.4.5 | |
| Jackson | **3.1.5** | see §3 |
| Testcontainers | **2.0.5** | see §4 |
| JUnit | 6.0.3 | |
| Flyway | 12.4.0 | |
| MySQL connector | 9.7.0 | |

Not managed by the BOM, so pinned in `pom.xml`: MapStruct 1.6.3,
**springdoc-openapi 3.1.1** (the 2.x line is Boot 3 only — 2.x on Boot 4 fails at
runtime), lombok-mapstruct-binding 0.2.0.

---

## 3. Jackson 3 — `tools.jackson.*`

Spring serializes with **Jackson 3** (`tools.jackson.databind`). Jackson 2
(`com.fasterxml.jackson`) is *also* on the classpath, pulled in transitively by
springdoc. So this compiles but is the wrong mapper:

```java
import com.fasterxml.jackson.databind.ObjectMapper;   // NOT the one Spring MVC uses
```

Use `tools.jackson.databind.ObjectMapper`, or inject the configured bean.

Jackson 3 also moved serialization features around. `WRITE_DATES_AS_TIMESTAMPS`
left `SerializationFeature` for `DateTimeFeature`, so the Boot 2/3 property

```properties
spring.jackson.serialization.write-dates-as-timestamps=false   # fails on Boot 4
```

now has to be

```properties
spring.jackson.datatype.datetime.write-dates-as-timestamps=false
```

The old spelling does not warn — it aborts startup with
`No enum constant tools.jackson.databind.SerializationFeature.write-dates-as-timestamps`.
(Jackson 3 already defaults it to `false`; it is set explicitly to pin the API
contract.)

---

## 4. Testcontainers 2 artifact and package renames

| Boot 3 era | Testcontainers 2 |
|---|---|
| `org.testcontainers:mysql` | `org.testcontainers:testcontainers-mysql` |
| `org.testcontainers:junit-jupiter` | `org.testcontainers:testcontainers-junit-jupiter` |
| `org.testcontainers.containers.MySQLContainer<SELF>` | `org.testcontainers.mysql.MySQLContainer` (no longer generic) |

The image is pinned to `mysql:8.4` rather than `mysql:latest`. Production
targets MySQL 8, and an unpinned tag silently changes the version under test the
first time a CI runner pulls a fresh image.

---

## 5. `@AutoConfigureMockMvc` moved package

Boot 4 reorganised test auto-configuration by module:

```java
// Boot 3
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
// Boot 4
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
```

---

## 6. Hikari autocommit must agree with Hibernate

`hibernate.connection.provider_disables_autocommit=true` is an *assertion* that
the pool already supplies connections with autocommit off. It lets Hibernate
defer acquiring a connection until the first statement, which shortens the time
a connection is held per request.

It is only safe when paired with:

```properties
spring.datasource.hikari.auto-commit=false
```

Set the Hibernate flag alone and Hibernate never disables autocommit, so **every
commit fails** with `Can't call commit when autocommit=true`. Both properties
are set together in `application.properties`; change them together or not at all.

---

## 7. `open-in-view` is off

```properties
spring.jpa.open-in-view=false
```

With it on (Boot's default), an unfetched lazy association silently loads during
JSON serialisation. The endpoint works in testing and quietly issues N+1 queries
in production. Off, the same code throws `LazyInitializationException`
immediately, so the fix — a fetch join or an entity graph — happens while the
query is being written.

Note this differs from `ems-office-project`, which sets it to `true`.

---

## 8. Flyway only; `ddl-auto=validate`

`validate`, never `update`. Hibernate compares every entity against the live
schema at startup and refuses to boot on a mismatch.

This is what makes the integration tests meaningful: they run against a real
MySQL 8.4 container with the real migrations, so a migration whose column type
or length drifts from its entity fails the build with the offending column
named, rather than failing in production on a value that does not fit.

Corollary: every new table needs the six `BaseEntity` columns — `id`,
`created_at`, `updated_at`, `created_by`, `updated_by`, `version`.

---

## 9. UTC in the database, IST on screen

`hibernate.jdbc.time_zone=UTC` and `spring.jackson.time-zone=UTC`. The API emits
ISO-8601 UTC; `DateUtils` converts to `Asia/Kolkata` for payslips, invoices and
emails.

`ems-office-project` instead stores IST and pre-formats dates as
`dd MMMM yyyy HH:mm` in JSON. That is convenient but lossy — the client cannot
tell the offset, and a server in another timezone produces different data. The
deliberate choice here is unambiguous transport, formatted at the edge.

The conversion genuinely matters: `2026-03-31T23:30:00Z` is **1 April** in
Kolkata. Getting it wrong files an attendance entry in the wrong month and
therefore in the wrong payroll run.

---

## 10. Errors in exactly one place

`GlobalExceptionHandler` is the only code that constructs an error response, and
`spring.mvc.problemdetails.enabled=false` keeps Spring's own handlers from
producing a second, differently-shaped error body.

Logging policy:

- **4xx → WARN, no stack trace.** Caller mistakes, not incidents.
- **5xx → ERROR with stack trace, and a generic response body.** Exception
  messages routinely carry SQL fragments, table names and file paths. The
  `correlationId` is the bridge between what the user can quote and what the log
  holds.

`ApiFieldError` additionally withholds the rejected value for fields whose names
look sensitive (password, token, Aadhaar, PAN, bank account), so a bad value
cannot be replayed out of a log aggregator or error tracker.

---

## 11. MapStruct with `unmappedTargetPolicy=ERROR`

Set as a compiler argument, so any target property that is neither mapped nor
explicitly ignored is a **compile error**.

The cost is an ignore list on every update mapper (see
`CompanySettingsMapper.applyUpdate`: id, the four audit columns, `version`, and
`logoPath`). The benefit is that adding a field to an entity without adding it
to the DTO stops the build, instead of shipping a field that silently never
saves. For payroll and invoicing that trade is clearly worth it.

Two related traps:

- **Processor order matters.** `annotationProcessorPaths` lists lombok, then
  `lombok-mapstruct-binding`, then `mapstruct-processor`. Get it wrong and
  MapStruct cannot see Lombok's generated accessors, and emits mapper
  implementations whose methods are empty — with no error.
- The `-Amapstruct.*` options are scoped to the `default-compile` execution
  only. Test sources have no `@Mapper`, so the processor does not claim the
  options there and javac reports them as unrecognised.

Generated implementations land in `target/generated-sources/annotations` — read
them when a mapping misbehaves.

---

## 12. The company profile is a single row with a fixed id

`company_settings` always has `id = 1`
(`CompanySettingsService.SINGLETON_ID`), seeded by migration `V1`. Every read is
a primary-key lookup and there can be no competing second profile.

The invariant is **not** enforced in the schema: MySQL rejects a `CHECK`
constraint that refers to an `AUTO_INCREMENT` column
(`Check constraint ... cannot refer to an auto-increment column`). Keeping
`AUTO_INCREMENT` for consistency with `BaseEntity`'s `IDENTITY` strategy was
preferred over dropping it to gain a schema-level guard, so the invariant lives
in the service.

Reads are cached (`@Cacheable`) and the update evicts — this row is consulted on
practically every page, email, payslip and invoice, and changes a few times a
year.

---

## 13. Mail is excluded from the health endpoint

```properties
management.health.mail.enabled=false
```

The mail indicator opens an SMTP connection on every health check and reports
`DOWN` on failure. That lets an SMTP outage — or just MailHog not running
locally — turn `/actuator/health` red and get the container killed by the
orchestrator. Email is sent asynchronously with retries, so the application is
healthy without it.

---

## 14. Phase 1 security is deliberately open

There is no user table yet. `SecurityConfig` permits all requests, because the
alternative is Boot locking everything behind a generated password with nothing
testable. Phase 2 replaces the `anyRequest().permitAll()` rule with
`.authenticated()` and adds the JWT filter.

What is already final and carries forward: stateless sessions, CORS from
configuration (empty by default, so a misconfigured deployment fails closed),
BCrypt strength 12, and the security headers — HSTS, `X-Content-Type-Options`,
`X-Frame-Options: DENY`, `Referrer-Policy`, and a CSP.

The CSP includes `'unsafe-inline'` for scripts and styles **only** because
Swagger UI, which this application serves itself, uses inline script and style.
The API's own responses are JSON and render nothing. Phase 11 should split the
filter chain so the API path gets a strict `default-src 'none'` policy and only
the Swagger paths get the relaxed one.

---

## 15. Planned for Phase 2: Nimbus JOSE rather than jjwt

The brief allows either. Nimbus is the better fit here:

- Spring Security 7 ships `NimbusJwtEncoder`/`NimbusJwtDecoder` in
  `spring-security-oauth2-jose`, version-managed by the Boot BOM.
- RS256 (the brief's preference) is what those classes are built for.
- `jjwt-jackson` binds to **Jackson 2** `jackson-databind`. With Jackson 3 being
  the version Spring actually uses (§3), adding a component with a hard Jackson 2
  dependency invites a classpath split that is tedious to debug.

CSRF approach to be recorded here once implemented: refresh token in an
`HttpOnly; Secure; SameSite=Strict` cookie scoped to `/api/v1/auth`, plus a
custom-header check on `/auth/refresh` and `/auth/logout`.

---

## 16. Divergences from `ems-office-project`

Intentional, per the brief. Noted because the new code will look unfamiliar.

| `ems-office-project` | Here | Why |
|---|---|---|
| package-by-layer (`controllers/`, `services/`) | package-by-feature | a change to payroll touches one directory |
| ModelMapper (runtime reflection) | MapStruct (compile-time) | mismatches are compile errors, and the generated code is readable |
| FreeMarker `.ftl` | Thymeleaf | per the brief |
| `ddl-auto=update` | Flyway + `validate` | reviewable, repeatable, reversible schema changes |
| `open-in-view=true` | `false` | see §7 |
| DB password, Gmail app password and JWT secret in committed `.properties` | env vars only, `.env` gitignored | a committed credential is permanently leaked |
| 1 test file / 207 classes | unit + Testcontainers tests per phase | payroll and GST arithmetic cannot be verified by hand |

Unrelated observation while reading that project's config: it uses
`spring.redis.host/port/password`, which is the pre-Boot-2.4 prefix and is
silently ignored — the correct prefix is `spring.data.redis.*`. It appears to
work only because Redis is on the default `localhost:6379`, and the configured
password is not being applied.

---

## 17. Phase 2: three bugs that all had the same cause

Found while writing the Phase 2 tests. Worth recording together, because they
are one mistake wearing three disguises and the pattern will recur.

**The rule: a failure path cannot write to its own transaction.** Spring rolls
back on `RuntimeException`. So any state change made just before throwing is
discarded — and on auth paths, rejecting the request *is* the normal outcome,
which means the records that matter most are exactly the ones that vanish.

| What broke | Symptom | Why it was dangerous |
|---|---|---|
| `failed_attempts++` inside `login()` | Counter stayed at 0 forever; account **never locked** | The lockout looked implemented and defended nothing. Unlimited password guesses. |
| `revokeFamily()` inside `rotate()` on reuse detection | Replay correctly returned 401, but the family was **not** revoked | Worst of the three. An attacker who had already rotated the stolen token kept a live session — the exact thing reuse detection exists to prevent. A test asserting only "replay gives 401" passes while the defence does nothing. |
| `revokeAllForUser()` on the disabled-account path | Disabled user's tokens stayed live | A disabled account could keep refreshing. |

Fixed by moving each into its own bean with
`@Transactional(propagation = REQUIRES_NEW)`: `LoginAttemptService` and
`RefreshTokenRevoker`. `AuditService` already worked this way for the same
reason.

**It must be a separate bean.** A `REQUIRES_NEW` method called from within the
same class bypasses the Spring proxy and silently inherits the caller's
transaction — reintroducing the bug in a form that looks correct.

The reuse-detection case is only caught by a test that checks the *successor*
token is dead too, not just that the replay was rejected. `AuthApiIT
.refreshRotationAndReuseDetection` does that in step 3, and
`docs/rbac.md` aside, it is the single most important assertion in the suite.

---

## 18. JWT numeric claims come back as `Long`

```java
Integer tokenVersion = jwt.getClaim("tokenVersion");   // ClassCastException
```

An `int` written into a JWT round-trips through JSON and is decoded as `Long`.
The cast threw on **every authenticated request**, so nothing worked at all —
loud, and therefore the easiest of the Phase 2 bugs to find. Read as `Number`
and compare with `intValue()`.

---

## 19. The per-email rate limit must exceed the lockout threshold

`app.auth.rate-limit.login-per-email.capacity` (10/min) sits deliberately above
`app.auth.lockout.max-attempts` (5).

If the rate limit bit first, the fifth wrong password would return 429 and the
account would never actually lock — leaving an attacker free to guess a few per
minute indefinitely. The two defences serve different purposes: the lockout
protects one account, the rate limit stops a flood. Lower the rate limit below
the lockout and you silently disable the lockout.

---

## 20. Authorities are rebuilt from the database on every request

`JwtUserAuthenticationConverter` loads the user on each authenticated request to
check the account is still enabled and `tokenVersion` still matches, then builds
authorities from the user's current roles — not from the token's `roles` and
`permissions` claims.

This costs one indexed primary-key read per request, with roles and permissions
fetched in the same statement. It is a deliberate trade against pure
statelessness: without it, revocation could not take effect until the access
token expired, so a departed employee's access to payroll would survive for up
to 15 minutes and an admin who clicked "disable" would be lied to.

The claims are still in the token, because a client needs them to decide which
menu items to render. They are advisory only — editing them client-side grants
nothing.

---

## 21. Lombok does not copy `@Qualifier` to generated constructors

`lombok.config` lists `@Qualifier`, `@Value` and `@Lazy` under
`lombok.copyableAnnotations`.

Without it, a `@Qualifier("handlerExceptionResolver")` field loses its qualifier
on the constructor `@RequiredArgsConstructor` generates. A web context holds
several `HandlerExceptionResolver` beans, so startup fails with
`NoUniqueBeanDefinitionException` pointing at a constructor that contains no
annotation to explain it. Three Phase 2 beans depend on this
(`DelegatingAuthenticationEntryPoint`, `DelegatingAccessDeniedHandler`,
`RateLimitFilter`).

---

## 22. 401 and 403 are produced by the same code as every other error

`DelegatingAuthenticationEntryPoint` and `DelegatingAccessDeniedHandler` do not
serialise JSON. They hand the exception to Spring MVC's
`HandlerExceptionResolver`, which dispatches it to `GlobalExceptionHandler`.

So a 401 raised deep in the security filter chain carries the same `errorCode`,
`timestamp`, `path` and `correlationId` as a 404 from a controller — with no
duplicated serialisation logic to drift out of step. `RateLimitFilter` uses the
same trick for its 429, which is why a filter-level rejection looks identical to
a service-level one.

---

## 23. CSRF: why `csrf().disable()` is correct here

There is no server-side session and no cookie that authenticates a request. The
access token travels in the `Authorization` header, which a browser never
attaches automatically — so the classic CSRF shape (browser silently
authenticates a forged cross-site request) cannot occur on the API.

The one cookie that exists is the refresh token, defended by:

- **`SameSite=Strict`** — never sent on a cross-site request, which is the
  primary control;
- **`Path=/api/v1/auth`** — only sent to the handful of endpoints that need it;
- **`HttpOnly`** — unreadable from script, so XSS cannot exfiltrate it;
- **a JSON content type requirement** on `/auth/refresh` — an HTML form, the only
  cross-site request that can POST without CORS preflight, cannot produce
  `application/json`.

Spring's CSRF token would add nothing, and would require every client to fetch
and echo a token for endpoints that are already protected. Revisit this if a
cookie is ever used to authenticate ordinary API calls.
