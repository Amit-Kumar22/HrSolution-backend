# Role–permission matrix

Generated from the seeded database (Flyway migration `V2`), which is the single
source of truth. `PermissionCatalogueIT` asserts this matrix against
`common/security/Permissions.java`, so the two cannot drift apart silently.

## How authorisation actually works

Three layers, and all three matter:

1. **Authentication** — `SecurityConfig` requires a valid access token for
   everything except sign-in, registration, account recovery, the public site
   feeds, health and Swagger.
2. **Permission** — `@PreAuthorize("hasAuthority('PAYROLL_PROCESS')")` on the
   controller method. Checked on **permission names, never role names**, so the
   matrix below can be re-pointed at runtime from
   `PUT /api/v1/roles/{id}/permissions` without a code change.
3. **Ownership** — enforced in the service. This is the layer people forget.
   A `CLIENT` holding `INVOICE_READ` may read invoices — *but only its own*.
   A `WORKER` holding `SELF_PAYSLIP_READ` may read payslips — *only their own*.
   The permission grants the capability; the service decides whose records it
   applies to.

Authorities are rebuilt from the database on **every request**, not read from
the token's `roles`/`permissions` claims. Those claims exist so a client can
decide what to render; editing them client-side grants nothing. The consequence
is that revoking a permission takes effect on the next request rather than
whenever a 15-minute token happens to expire.

## Roles

| Code | Role | Who holds it |
|---|---|---|
| SA | `SUPER_ADMIN` | Company owner. Everything, including system settings and role editing. |
| AD | `ADMIN` | Operations head. All modules **except** system settings and role editing. |
| HR | `HR_RECRUITER` | Recruitment team. |
| OM | `OPERATIONS_MANAGER` | Deployment team. |
| SS | `SITE_SUPERVISOR` | Field supervisor; attendance for assigned sites only. |
| AC | `ACCOUNTS` | Finance team. |
| CL | `CLIENT` | Client company user — own data only. |
| WK | `WORKER` | Deployed employee — own data only. |
| CD | `CANDIDATE` | Job seeker — own data only. |

## The matrix

| Permission | SA | AD | HR | OM | SS | AC | CL | WK | CD |
|---|---|---|---|---|---|---|---|---|---|
| **USER** | | | | | | | | | |
| `ROLE_MANAGE` | ✅ | · | · | · | · | · | · | · | · |
| `USER_MANAGE` | ✅ | ✅ | · | · | · | · | · | · | · |
| `USER_READ` | ✅ | ✅ | · | · | · | · | · | · | · |
| **CLIENT** | | | | | | | | | |
| `CLIENT_APPROVE` | ✅ | ✅ | · | · | · | · | · | · | · |
| `CLIENT_READ` | ✅ | ✅ | ✅ | ✅ | · | ✅ | · | · | · |
| `CLIENT_WRITE` | ✅ | ✅ | · | ✅ | · | · | · | · | · |
| **REQUISITION** | | | | | | | | | |
| `REQUISITION_APPROVE` | ✅ | ✅ | · | ✅ | · | · | · | · | · |
| `REQUISITION_CREATE` | ✅ | ✅ | · | ✅ | · | · | ✅ | · | · |
| `REQUISITION_READ` | ✅ | ✅ | ✅ | ✅ | · | · | ✅ | · | · |
| **RECRUITMENT** | | | | | | | | | |
| `APPLICATION_MANAGE` | ✅ | ✅ | ✅ | · | · | · | · | · | · |
| `APPLICATION_READ` | ✅ | ✅ | ✅ | · | · | · | · | · | · |
| `CANDIDATE_READ` | ✅ | ✅ | ✅ | · | · | · | · | · | · |
| `CANDIDATE_WRITE` | ✅ | ✅ | ✅ | · | · | · | · | · | · |
| `JOB_MANAGE` | ✅ | ✅ | ✅ | · | · | · | · | · | · |
| `JOB_READ` | ✅ | ✅ | ✅ | · | · | · | · | · | ✅ |
| **WORKER** | | | | | | | | | |
| `WORKER_READ` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | · | · |
| `WORKER_SENSITIVE_READ` | ✅ | ✅ | · | · | · | ✅ | · | · | · |
| `WORKER_WRITE` | ✅ | ✅ | ✅ | ✅ | · | · | · | · | · |
| **DEPLOYMENT** | | | | | | | | | |
| `DEPLOYMENT_MANAGE` | ✅ | ✅ | · | ✅ | · | · | · | · | · |
| `DEPLOYMENT_READ` | ✅ | ✅ | · | ✅ | ✅ | · | ✅ | · | · |
| **ATTENDANCE** | | | | | | | | | |
| `ATTENDANCE_APPROVE` | ✅ | ✅ | · | ✅ | · | · | ✅ | · | · |
| `ATTENDANCE_MARK` | ✅ | ✅ | · | ✅ | ✅ | · | · | · | · |
| `ATTENDANCE_READ` | ✅ | ✅ | · | ✅ | ✅ | ✅ | ✅ | · | · |
| `LEAVE_APPROVE` | ✅ | ✅ | · | ✅ | · | · | · | · | · |
| **PAYROLL** | | | | | | | | | |
| `PAYROLL_PROCESS` | ✅ | ✅ | · | · | · | ✅ | · | · | · |
| `PAYROLL_READ` | ✅ | ✅ | · | · | · | ✅ | · | · | · |
| **BILLING** | | | | | | | | | |
| `INVOICE_MANAGE` | ✅ | ✅ | · | · | · | ✅ | · | · | · |
| `INVOICE_READ` | ✅ | ✅ | · | · | · | ✅ | ✅ | · | · |
| `PAYMENT_MANAGE` | ✅ | ✅ | · | · | · | ✅ | · | · | · |
| **COMPLIANCE** | | | | | | | | | |
| `COMPLIANCE_MANAGE` | ✅ | ✅ | · | · | · | ✅ | · | · | · |
| `COMPLIANCE_READ` | ✅ | ✅ | · | · | · | ✅ | · | · | · |
| **CONTENT** | | | | | | | | | |
| `CONTENT_MANAGE` | ✅ | ✅ | · | · | · | · | · | · | · |
| **REPORT** | | | | | | | | | |
| `REPORT_VIEW` | ✅ | ✅ | ✅ | ✅ | · | ✅ | · | · | · |
| **ENQUIRY** | | | | | | | | | |
| `ENQUIRY_MANAGE` | ✅ | ✅ | · | · | · | · | · | · | · |
| **SETTINGS** | | | | | | | | | |
| `SETTINGS_MANAGE` | ✅ | · | · | · | · | · | · | · | · |
| **AUDIT** | | | | | | | | | |
| `AUDIT_VIEW` | ✅ | ✅ | · | · | · | · | · | · | · |
| **SELF** | | | | | | | | | |
| `SELF_APPLICATION_MANAGE` | ✅ | · | · | · | · | · | · | · | ✅ |
| `SELF_ATTENDANCE_READ` | ✅ | · | · | · | · | · | · | ✅ | · |
| `SELF_LEAVE_MANAGE` | ✅ | · | · | · | · | · | · | ✅ | · |
| `SELF_PAYSLIP_READ` | ✅ | · | · | · | · | · | · | ✅ | · |
| `SELF_PROFILE_MANAGE` | ✅ | · | · | · | ✅ | · | ✅ | ✅ | ✅ |

Counts: **SA** 41, **AD** 34, **HR** 11, **OM** 14, **SS** 5, **AC** 12, **CL** 8, **WK** 4, **CD** 3

## Deliberate separations

These are design decisions, not omissions. `PermissionCatalogueIT` locks each
one in place.

**`ADMIN` is not `SUPER_ADMIN`.** An ADMIN runs the business day to day but
holds neither `SETTINGS_MANAGE` nor `ROLE_MANAGE`. Without that split, an ADMIN
could edit the statutory PF/ESI percentages or grant themselves any permission,
and the distinction between the two roles would be cosmetic. `UserAdminService`
additionally refuses to let anyone grant `SUPER_ADMIN` unless they already hold
it, and refuses to let anyone change their own roles or disable themselves.

**`SITE_SUPERVISOR` can mark attendance but not approve it.** Separation of
duties: the attendance sheet feeds both payroll and client billing, so whoever
records the hours must not also be the one who signs them off.

**`ACCOUNTS` cannot deploy workers or edit jobs**, and `OPERATIONS_MANAGER`
cannot process payroll. The team that decides who works where is not the team
that decides what they are paid.

**`WORKER` and `CANDIDATE` hold nothing but `SELF_*`** (plus `JOB_READ` for a
candidate, since job adverts are public anyway). A worker reading another
worker's payslip would be a data breach, so there is no permission that could
let them.

**External roles hold no `WORKER_SENSITIVE_READ`.** Unmasked Aadhaar, PAN and
bank account numbers are visible only to `SUPER_ADMIN` and `ACCOUNTS`, who need
them to file returns and run bank transfers. Everyone else sees
`XXXX-XXXX-1234`.

**`CONTENT_MANAGE` is separate from `SETTINGS_MANAGE`.** Rewording a services
page or adding a manpower category is routine marketing and master-data work,
and both SUPER_ADMIN and ADMIN hold it. `SETTINGS_MANAGE` reaches statutory
configuration, the GST rate and the invoice number series, so it stays with
SUPER_ADMIN alone. Reading the manpower category list needs no permission at
all beyond being signed in — requisition, deployment and payroll screens all
depend on it.

## Changing the matrix

```http
PUT /api/v1/roles/{roleId}/permissions
Authorization: Bearer <token of a SUPER_ADMIN>
Content-Type: application/json

{ "permissions": ["CLIENT_READ", "CLIENT_WRITE"] }
```

Full replacement — send every permission the role should hold. An empty array
strips it entirely. Unknown names are rejected with a field error rather than
silently dropped, because a typo that quietly grants nothing is far harder to
notice than an error.

`SUPER_ADMIN` cannot be edited. It is the role that can repair every other
role's permissions, so allowing it to strip its own would be a one-click route
to a system nobody can administer.

Every change is written to `audit_logs` as `ROLE_PERMISSIONS_CHANGED` with the
before and after sets.

## Adding a permission

1. Add the constant to `common/security/Permissions.java`, **and** to its `ALL` set.
2. Add the `INSERT` to a new Flyway migration, plus the `role_permissions` rows.
3. Annotate the endpoint: `@PreAuthorize("hasAuthority('" + Permissions.YOUR_NEW_ONE + "')")`.
4. Add the ownership check in the service if the permission is scoped to a
   client, worker or site.

Skip step 1 or 2 and `PermissionCatalogueIT` fails the build — which is the
point, because neither mistake shows up at runtime.
