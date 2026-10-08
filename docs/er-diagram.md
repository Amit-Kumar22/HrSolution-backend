# ER diagram

Target schema for the whole platform. Tables are grouped by module, and each is
tagged with the phase that creates it — so this doubles as the schema roadmap.

**Created so far (Phases 1–3):**

- **V1** — `company_settings`
- **V2** — `users`, `roles`, `permissions`, `role_permissions`, `user_roles`,
  `refresh_tokens`, `verification_tokens`, `password_reset_tokens`, `audit_logs`
- **V3** — `manpower_categories`, `service_offerings`, `industries`,
  `testimonials`, `enquiries`, `contact_messages`, `documents`

Note that `manpower_categories` moved forward from Phase 4 to Phase 3: the public
enquiry form needs a category dropdown, and a free-text field would produce
unusable data on the one form the sales team actually reads. Phase 4 builds
skills and rate cards on top of it rather than creating it.

Two tables were also added that the original plan did not list —
`service_offerings` and `industries` — because the marketing copy has to be
editable without a deployment, which is the whole reason they are rows rather
than template files.

Every table inherits the `BaseEntity` columns and they are omitted from the
diagrams below to keep them readable:

| Column | Type | |
|---|---|---|
| `id` | `BIGINT` | PK, `AUTO_INCREMENT` |
| `created_at` | `DATETIME(6)` | not null |
| `updated_at` | `DATETIME(6)` | not null |
| `created_by` | `VARCHAR(150)` | principal, or `system` |
| `updated_by` | `VARCHAR(150)` | |
| `version` | `BIGINT` | optimistic locking |

Business records that must never be hard-deleted additionally carry `deleted`,
`deleted_at` and `deleted_by` (`SoftDeletableEntity`): clients, workers,
candidates, deployments, payroll runs, invoices.

---

## 1. Identity, access control and sessions — Phase 2

```mermaid
erDiagram
    users {
        varchar email UK "login identifier"
        varchar password_hash "BCrypt, strength 12"
        varchar first_name
        varchar last_name
        varchar phone
        enum status "PENDING_VERIFICATION|PENDING_APPROVAL|ACTIVE|DISABLED|LOCKED"
        boolean email_verified
        int failed_attempts "lockout after 5"
        datetime locked_until "15 minute lockout"
        int token_version "increment to kill all access tokens"
        datetime last_login_at
    }
    roles {
        varchar name UK "SUPER_ADMIN|ADMIN|HR_RECRUITER|OPERATIONS_MANAGER|SITE_SUPERVISOR|ACCOUNTS|CLIENT|WORKER|CANDIDATE"
        varchar description
        boolean system_role "cannot be deleted"
    }
    permissions {
        varchar name UK "CLIENT_WRITE, PAYROLL_PROCESS, ..."
        varchar module "grouping for the admin UI"
    }
    user_roles { bigint user_id FK  bigint role_id FK }
    role_permissions { bigint role_id FK  bigint permission_id FK }
    refresh_tokens {
        bigint user_id FK
        varchar token_hash UK "SHA-256; plaintext never stored"
        varchar family_id "reuse detection: replay revokes the family"
        bigint replaced_by_token_id FK "rotation chain"
        datetime expires_at
        datetime revoked_at
        varchar ip_address
        varchar user_agent "shown on the sessions screen"
    }
    verification_tokens {
        bigint user_id FK
        varchar token_hash UK
        datetime expires_at "24h"
        datetime used_at "single use"
    }
    password_reset_tokens {
        bigint user_id FK
        varchar token_hash UK
        datetime expires_at "15-30 min"
        datetime used_at "single use"
    }

    users ||--o{ user_roles : "assigned"
    roles ||--o{ user_roles : "granted to"
    roles ||--o{ role_permissions : "grants"
    permissions ||--o{ role_permissions : "granted by"
    users ||--o{ refresh_tokens : "device sessions"
    refresh_tokens |o--o| refresh_tokens : "replaced by"
    users ||--o{ verification_tokens : "email verification"
    users ||--o{ password_reset_tokens : "password reset"
```

Authorization is checked on **permissions**, not role names
(`@PreAuthorize("hasAuthority('PAYROLL_PROCESS')")`), so the role→permission
mapping can change in the admin UI without touching code. Full matrix will be in
`docs/rbac.md`.

---

## 2. Configuration and statutory rates — Phases 1, 3 and 8

```mermaid
erDiagram
    company_settings {
        varchar legal_name
        varchar trade_name
        varchar state_code "GST state code; drives CGST+SGST vs IGST"
        varchar gstin
        varchar pan
        varchar pf_establishment_code
        varchar esi_establishment_code
        varchar bank_account_number "printed on invoices"
        varchar logo_path
    }
    statutory_configs {
        enum type "PF|ESI|LWF|BONUS|GRATUITY|GST|TDS"
        varchar state "null = applies nationally"
        decimal employee_percent
        decimal employer_percent
        decimal wage_ceiling "PF 15000, ESI 21000"
        decimal admin_charge_percent
        date effective_from "never edit history - add a new row"
        date effective_to
    }
    pt_slabs {
        varchar state
        decimal monthly_wage_from
        decimal monthly_wage_to
        decimal tax_amount
        int month "some states charge extra in one month"
        date effective_from
    }
    minimum_wages {
        varchar state
        enum skill_level "UNSKILLED|SEMI_SKILLED|SKILLED|HIGHLY_SKILLED"
        varchar zone "states band by area"
        decimal basic_per_day
        decimal vda_per_day "revised twice yearly"
        date effective_from
    }
    number_series {
        enum type "INVOICE|EMPLOYEE_CODE|CREDIT_NOTE"
        varchar financial_year "2026-27"
        varchar prefix
        bigint last_number "numbers are never reused"
    }
```

**No rate is ever hardcoded.** Every statutory value is a row with an
`effective_from` date, so recomputing a prior month uses the rates that applied
*then*. Correcting a rate means inserting a new row, never editing an old one.

---

## 3. Clients, sites, contracts and rate cards — Phase 4

```mermaid
erDiagram
    clients {
        varchar legal_name
        varchar gstin
        varchar pan
        varchar state "compared with company state_code for GST"
        varchar state_code
        enum status "PENDING_APPROVAL|ACTIVE|SUSPENDED|INACTIVE"
        int payment_terms_days
    }
    client_users {
        bigint client_id FK
        bigint user_id FK "a CLIENT-role login"
        boolean primary_contact
        varchar designation
    }
    client_sites {
        bigint client_id FK
        varchar site_name
        varchar address_line1
        varchar city
        varchar state "site state, which can differ from billing state"
        varchar site_incharge_name
    }
    client_contracts {
        bigint client_id FK
        varchar contract_number
        date start_date
        date end_date "expiry reminders at 60/30/7 days"
        enum service_charge_type "PERCENTAGE|FIXED_PER_WORKER"
        decimal service_charge_value
        varchar document_path
        enum status "DRAFT|ACTIVE|EXPIRED|TERMINATED"
    }
    rate_cards {
        bigint client_id FK
        bigint category_id FK
        bigint site_id FK "null = all sites"
        decimal monthly_wage
        decimal billing_rate
        decimal ot_rate_per_hour
        int shift_hours
        date effective_from
    }
    manpower_categories {
        varchar name UK "Security Guard, Helper, Electrician, ..."
        enum skill_level "maps to the minimum wage band"
        varchar description
    }
    skills { varchar name UK }
    category_skills { bigint category_id FK  bigint skill_id FK }

    clients ||--o{ client_users : "portal logins"
    clients ||--o{ client_sites : "operates"
    clients ||--o{ client_contracts : "signs"
    clients ||--o{ rate_cards : "priced by"
    client_sites ||--o{ rate_cards : "site override"
    manpower_categories ||--o{ rate_cards : "for"
    manpower_categories ||--o{ category_skills : "requires"
    skills ||--o{ category_skills : "required by"
```

---

## 4. Requisitions and recruitment — Phases 5 and 6

```mermaid
erDiagram
    requisitions {
        bigint client_id FK
        bigint site_id FK
        bigint category_id FK
        int quantity_required
        int quantity_deployed "fulfilment progress"
        enum skill_level
        enum shift "DAY|NIGHT|ROTATIONAL"
        date required_from
        enum status "DRAFT|SUBMITTED|APPROVED|IN_PROGRESS|PARTIALLY_FULFILLED|FULFILLED|CLOSED|REJECTED|CANCELLED"
    }
    requisition_status_history {
        bigint requisition_id FK
        enum from_status
        enum to_status
        bigint changed_by_user_id FK
        varchar remarks
    }
    jobs {
        bigint requisition_id FK "optional link"
        bigint category_id FK
        varchar title
        varchar location
        decimal salary_min
        decimal salary_max
        int openings
        boolean published
        date expires_at
    }
    candidates {
        bigint user_id FK "null for guest applications"
        varchar full_name
        varchar email
        varchar phone
        int total_experience_months
        varchar resume_path
        boolean consent_given "DPDP Act"
    }
    applications {
        bigint job_id FK
        bigint candidate_id FK
        enum status "APPLIED|SCREENING|SHORTLISTED|INTERVIEW_SCHEDULED|SELECTED|OFFERED|JOINED|REJECTED|ON_HOLD|WITHDRAWN"
        date applied_on
    }
    application_status_history {
        bigint application_id FK
        enum from_status
        enum to_status
        bigint changed_by_user_id FK
        varchar notes
    }
    interviews {
        bigint application_id FK
        datetime scheduled_at
        enum mode "IN_PERSON|PHONE|VIDEO"
        bigint interviewer_user_id FK
        enum result "PENDING|PASS|FAIL|NO_SHOW"
        varchar feedback
    }

    requisitions ||--o{ requisition_status_history : "audited by"
    requisitions ||--o{ jobs : "sourced by"
    jobs ||--o{ applications : "receives"
    candidates ||--o{ applications : "submits"
    applications ||--o{ application_status_history : "audited by"
    applications ||--o{ interviews : "schedules"
```

---

## 5. Workers and deployment — Phase 6

```mermaid
erDiagram
    workers {
        varchar employee_code UK "EMP-2026-00001"
        bigint user_id FK "WORKER-role login"
        bigint candidate_id FK "set when converted on JOINED"
        varchar first_name
        varchar last_name
        date date_of_birth
        date date_of_joining
        varchar aadhaar_encrypted "AES-GCM; masked in API responses"
        varchar pan_encrypted "AES-GCM"
        varchar uan "PF universal account number"
        varchar esic_ip_number
        enum status "ACTIVE|ON_BENCH|DEPLOYED|ON_LEAVE|RESIGNED|TERMINATED"
        enum police_verification_status
        date last_working_day
        boolean full_and_final_settled
    }
    worker_bank_details {
        bigint worker_id FK
        varchar account_number_encrypted "AES-GCM"
        varchar ifsc
        varchar bank_name
        boolean primary_account
    }
    worker_documents {
        bigint worker_id FK
        enum document_type "AADHAAR|PAN|PHOTO|POLICE_VERIFICATION|EDUCATION|EXPERIENCE|MEDICAL"
        varchar file_path
        enum verification_status "PENDING|VERIFIED|REJECTED"
        date expires_at "drives expiry reminders"
    }
    salary_structures {
        bigint worker_id FK
        decimal basic
        decimal da
        decimal hra
        decimal conveyance
        decimal other_allowance
        decimal ot_rate_per_hour
        date effective_from
    }
    deployments {
        bigint worker_id FK
        bigint client_id FK
        bigint site_id FK
        bigint requisition_id FK
        date start_date
        date end_date "null = currently deployed"
        enum shift
        decimal monthly_wage "defaulted from the rate card"
        decimal billing_rate
        enum status "ACTIVE|TRANSFERRED|RELEASED|COMPLETED"
    }

    workers ||--o{ worker_bank_details : "paid into"
    workers ||--o{ worker_documents : "evidenced by"
    workers ||--o{ salary_structures : "paid per"
    workers ||--o{ deployments : "posted via"
```

Sensitive columns (Aadhaar, PAN, bank account) are encrypted at rest with
AES-GCM through a JPA `AttributeConverter`, keyed by `FIELD_ENCRYPTION_KEY`, and
returned masked (`XXXX-XXXX-1234`) unless the caller holds the specific
permission to see them.

A worker must never hold two overlapping `ACTIVE` deployments —
`DateUtils.overlaps` (already built and tested in Phase 1) enforces it, treating
a null `end_date` as open-ended.

---

## 6. Attendance and leave — Phase 7

```mermaid
erDiagram
    attendance_sheets {
        bigint client_id FK
        bigint site_id FK
        int month
        int year
        enum status "DRAFT|SUBMITTED|CLIENT_APPROVED|LOCKED"
        datetime locked_at "locked once used by payroll"
        varchar unlock_reason "admin unlock is audited"
    }
    attendance_entries {
        bigint sheet_id FK
        bigint worker_id FK
        bigint deployment_id FK
        date attendance_date
        enum status "PRESENT|ABSENT|HALF_DAY|WEEK_OFF|HOLIDAY|LEAVE"
        decimal overtime_hours
    }
    leave_requests {
        bigint worker_id FK
        date from_date
        date to_date
        enum leave_type "CASUAL|SICK|EARNED|UNPAID"
        enum status "PENDING|APPROVED|REJECTED|CANCELLED"
        bigint approved_by_user_id FK
    }
    holidays {
        varchar name
        date holiday_date
        varchar state "null = national"
        boolean mandatory
    }

    attendance_sheets ||--o{ attendance_entries : "contains"
```

A sheet is unique on (site, month, year). Once `LOCKED` it is immutable; editing
requires an admin unlock with a recorded reason, because payroll has already
consumed it.

---

## 7. Payroll — Phase 8

```mermaid
erDiagram
    payroll_runs {
        bigint client_id FK "null = all clients"
        int month
        int year
        enum status "DRAFT|PROCESSED|APPROVED|PAID|LOCKED"
        decimal total_gross
        decimal total_deductions
        decimal total_net
        bigint approved_by_user_id FK
    }
    payroll_items {
        bigint payroll_run_id FK
        bigint worker_id FK
        bigint deployment_id FK
        bigint attendance_sheet_id FK "the locked source"
        int paid_days
        int total_days
        decimal overtime_hours
        decimal gross_earnings
        decimal total_deductions
        decimal net_pay
        boolean below_minimum_wage "warning flag"
    }
    payroll_item_components {
        bigint payroll_item_id FK
        enum component_type "EARNING|DEDUCTION|EMPLOYER_CONTRIBUTION"
        varchar code "BASIC|DA|HRA|OT|PF_EE|ESI_EE|PT|ADVANCE|PF_ER|ESI_ER"
        decimal amount
        decimal computed_on "the base the rate was applied to"
        decimal rate_percent "the rate used, captured for audit"
    }

    payroll_runs ||--o{ payroll_items : "contains"
    payroll_items ||--o{ payroll_item_components : "broken down into"
```

`payroll_item_components` stores the rate and base used for every figure, not
just the result. Without that, a payslip queried a year later cannot be
explained — and PF/ESI rates change.

---

## 8. Billing and invoicing — Phase 9

```mermaid
erDiagram
    invoices {
        varchar invoice_number UK "INV/2026-27/0001, never reused"
        bigint client_id FK
        bigint payroll_run_id FK
        date invoice_date
        date due_date
        varchar place_of_supply
        decimal taxable_amount
        decimal cgst_amount "same-state"
        decimal sgst_amount "same-state"
        decimal igst_amount "inter-state"
        decimal total_amount
        decimal amount_paid
        enum status "DRAFT|ISSUED|PARTIALLY_PAID|PAID|CANCELLED"
    }
    invoice_lines {
        bigint invoice_id FK
        varchar description
        varchar sac_code
        enum line_type "WAGES|EMPLOYER_PF|EMPLOYER_ESI|SERVICE_CHARGE|REIMBURSEMENT"
        decimal quantity
        decimal rate
        decimal amount
        decimal gst_rate
    }
    payments {
        bigint invoice_id FK
        date payment_date
        decimal amount
        enum mode "NEFT|RTGS|CHEQUE|UPI|CASH"
        varchar reference_number
        decimal tds_deducted "clients deduct TDS at source"
    }
    credit_notes {
        varchar credit_note_number UK
        bigint invoice_id FK
        date issue_date
        decimal amount
        varchar reason
    }

    invoices ||--o{ invoice_lines : "itemised by"
    invoices ||--o{ payments : "settled by"
    invoices ||--o{ credit_notes : "corrected by"
```

GST: `CGST + SGST` when the client's state code equals the company's, otherwise
`IGST`. An issued invoice is never edited — corrections are credit notes, which
is both the GST requirement and a sound audit rule.

---

## 9. Compliance, documents, notifications and audit — Phases 3 and 10

```mermaid
erDiagram
    licences {
        enum licence_type "CONTRACT_LABOUR|PSARA|GST|PF|ESI|SHOP_ESTABLISHMENT|TRADE|ISO"
        varchar licence_number
        bigint client_id FK "contract labour licences are per establishment"
        date issue_date
        date expiry_date "reminders at 60/30/7 days"
        varchar document_path
        enum status "ACTIVE|EXPIRED|UNDER_RENEWAL"
    }
    statutory_challans {
        enum challan_type "PF|ESI|PT|LWF|TDS"
        int month
        int year
        decimal amount
        date paid_on
        varchar challan_number
        varchar document_path
        boolean return_filed
    }
    documents {
        enum owner_type "WORKER|CLIENT|CONTRACT|LICENCE|CANDIDATE|CHALLAN"
        bigint owner_id "polymorphic owner"
        varchar file_name
        varchar content_type "validated by content, not extension"
        bigint size_bytes
        varchar storage_key "resolved by StorageService"
    }
    enquiries {
        varchar company_name
        varchar contact_person
        varchar phone
        varchar email
        varchar city
        bigint category_id FK
        int number_of_workers
        date required_from
        enum status "NEW|CONTACTED|QUALIFIED|CONVERTED|CLOSED"
    }
    contact_messages { varchar name  varchar email  varchar subject  varchar message  boolean read }
    testimonials {
        varchar client_name
        varchar designation
        varchar content
        int rating
        boolean published
        varchar logo_path
    }
    notifications {
        bigint user_id FK
        varchar title
        varchar message
        enum type "INFO|WARNING|ACTION_REQUIRED"
        varchar link
        boolean read
    }
    audit_logs {
        varchar entity_type
        bigint entity_id
        enum action "CREATE|UPDATE|DELETE|LOGIN_SUCCESS|LOGIN_FAILURE|LOCKOUT|TOKEN_REUSE_DETECTED|ROLE_CHANGE"
        bigint user_id FK
        json old_values
        json new_values
        varchar ip_address
        varchar correlation_id "ties back to the request log"
    }
```

`audit_logs.correlation_id` is the same value `CorrelationIdFilter` puts on every
log line and in every error response — so an audit row, the application log and
the error the user saw can all be joined after the fact.

---

## Full table list by phase

| Phase | Tables |
|---|---|
| 1 | `company_settings` |
| 2 | `users`, `roles`, `permissions`, `user_roles`, `role_permissions`, `refresh_tokens`, `verification_tokens`, `password_reset_tokens`, `audit_logs` |
| 3 | `enquiries`, `contact_messages`, `testimonials`, `documents`, `service_offerings`, `industries`, `manpower_categories` (moved up from Phase 4) |
| 4 | `clients`, `client_users`, `client_sites`, `client_contracts`, `rate_cards`, `skills`, `category_skills` |
| 5 | `jobs`, `candidates`, `applications`, `application_status_history`, `interviews` |
| 6 | `requisitions`, `requisition_status_history`, `workers`, `worker_bank_details`, `worker_documents`, `salary_structures`, `deployments` |
| 7 | `attendance_sheets`, `attendance_entries`, `leave_requests`, `holidays` |
| 8 | `payroll_runs`, `payroll_items`, `payroll_item_components`, `statutory_configs`, `pt_slabs`, `minimum_wages`, `number_series` |
| 9 | `invoices`, `invoice_lines`, `payments`, `credit_notes` |
| 10 | `licences`, `statutory_challans`, `notifications` |
