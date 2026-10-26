# Summary (For Next Work)

**********************************************
## 1: Payroll
**********************************************

### Payroll Runs
**Base:** `/api/v1/payroll` (Auth required, `PAYROLL_READ/MANAGE/APPROVE`)

| Method | Endpoint | Permission | Notes |
|---|---|---|---|
| GET | `/api/v1/payroll/runs` | `PAYROLL_READ` | List payroll runs (client-scoped) |
| GET | `/api/v1/payroll/runs/{id}` | `PAYROLL_READ` | Get payroll run with items and components |
| POST | `/api/v1/payroll/runs` | `PAYROLL_MANAGE` | Create payroll run for client/period |
| POST | `/api/v1/payroll/runs/{id}/generate` | `PAYROLL_MANAGE` | Generate payroll items from locked attendance sheets |
| POST | `/api/v1/payroll/runs/{id}/calculate` | `PAYROLL_MANAGE` | Calculate statutory components (PF/ESI/PT) |
| POST | `/api/v1/payroll/runs/{id}/submit` | `PAYROLL_MANAGE` | Submit run for approval |
| POST | `/api/v1/payroll/runs/{id}/approve` | `PAYROLL_APPROVE` | Approve payroll run |
| POST | `/api/v1/payroll/runs/{id}/lock` | `PAYROLL_APPROVE` | Lock run (immutable after lock) |
| GET | `/api/v1/payroll/runs/{id}/payslips` | `PAYROLL_READ` | Get payslip data for all items |

**Statuses:** `DRAFT | GENERATED | CALCULATED | SUBMITTED | APPROVED | LOCKED`  
**Key:** Components store rate+base. Locked runs are immutable. Only consumes LOCKED attendance sheets.

**********************************************
## 2: Billing & Invoicing
**********************************************

### Invoices
**Base:** `/api/v1/invoices` (Auth required, `INVOICE_READ/INVOICE_MANAGE/PAYMENT_MANAGE`)

| Method | Endpoint | Permission | Notes |
|---|---|---|---|
| GET | `/api/v1/invoices` | `INVOICE_READ` | List invoices (client-scoped) |
| GET | `/api/v1/invoices/{id}` | `INVOICE_READ` | Get invoice with lines, payments, credit notes |
| POST | `/api/v1/invoices/from-payroll` | `INVOICE_MANAGE` | Generate invoice from approved/locked payroll run |
| PATCH | `/api/v1/invoices/{id}/issue` | `INVOICE_MANAGE` | Issue DRAFT invoice |
| POST | `/api/v1/invoices/{id}/payments` | `PAYMENT_MANAGE` | Record payment (updates status to PARTIALLY_PAID/PAID) |
| POST | `/api/v1/invoices/{id}/credit-notes` | `INVOICE_MANAGE` | Apply credit note against issued invoice |
| PATCH | `/api/v1/invoices/{id}/cancel` | `INVOICE_MANAGE` | Cancel DRAFT invoice only |

**Invoice Statuses:** `DRAFT | ISSUED | PARTIALLY_PAID | PAID | CANCELLED`  
**GST Rules:** CGST+SGST if client billing state equals company state; IGST if different; **missing billing state code rejects invoice generation**.  
**Numbering:** `INV/YYYY-YY/NNNN`  
**Critical:** Issued invoices are immutable. Corrections only via credit notes.

**********************************************
## 3: Compliance, Notifications, Reports
**********************************************

### Licences
**Base:** `/api/v1/licences` (Auth required, `COMPLIANCE_READ/MANAGE`)

| Method | Endpoint | Permission | Notes |
|---|---|---|---|
| GET | `/api/v1/licences` | `COMPLIANCE_READ` | List licences (can filter by clientId) |
| POST | `/api/v1/licences` | `COMPLIANCE_MANAGE` | Create licence |
| PUT | `/api/v1/licences/{id}` | `COMPLIANCE_MANAGE` | Update licence |

### Compliance & Reminders
**Base:** `/api/v1/compliance` (Auth required, `COMPLIANCE_READ/MANAGE`)

| Method | Endpoint | Permission | Notes |
|---|---|---|---|
| GET | `/api/v1/compliance/reminders?withinDays=` | `COMPLIANCE_READ` | Get expiring licences with reminder level (CRITICAL/HIGH/MEDIUM) |
| POST | `/api/v1/compliance/reminders/generate` | `COMPLIANCE_MANAGE` | Generate in-app notifications for expiring licences (60 days) |

### Statutory Challans
**Base:** `/api/v1/challans` (Auth required, `COMPLIANCE_READ/MANAGE`)

| Method | Endpoint | Permission | Notes |
|---|---|---|---|
| GET | `/api/v1/challans` | `COMPLIANCE_READ` | List challans by period |
| POST | `/api/v1/challans` | `COMPLIANCE_MANAGE` | Create/update statutory challan (PF/ESI/PT/LWF/TDS) |

### Notifications
**Base:** `/api/v1/notifications` (Auth required)

| Method | Endpoint | Permission | Notes |
|---|---|---|---|
| GET | `/api/v1/notifications` | Authenticated | List unread notifications for current user |
| GET | `/api/v1/notifications/unread-count` | Authenticated | Get unread count |
| PATCH | `/api/v1/notifications/{id}/read` | Authenticated | Mark notification as read |
| POST | `/api/v1/notifications/read-all` | Authenticated | Mark all notifications as read |

### Reports
**Base:** `/api/v1/reports` (Auth required, `REPORT_VIEW`)

| Method | Endpoint | Permission | Notes |
|---|---|---|---|
| GET | `/api/v1/reports/attendance?month=&year=` | `REPORT_VIEW` | Attendance report (placeholder) |
| GET | `/api/v1/reports/payroll?month=&year=` | `REPORT_VIEW` | Payroll report (placeholder) |
| GET | `/api/v1/reports/billing/ageing` | `REPORT_VIEW` | Billing ageing report (placeholder) |
| GET | `/api/v1/reports/compliance` | `REPORT_VIEW` | Compliance report (placeholder) |

**Reminder Levels:** CRITICAL (≤7 days) | HIGH (≤30 days) | MEDIUM (≤60 days)  
**Notification Types:** `INFO | WARNING | ACTION_REQUIRED`

## Important Notes for Frontend

- **Payroll flow:** Generate from LOCKED attendance → Calculate (statutory) → Submit → Approve → Lock
- **Billing from payroll:** Only generate invoice from payroll run; GST requires client billing state
- **Invoice immutability:** Once ISSUED, cannot edit - use credit notes for corrections
- **Payments:** Recording payment auto-updates invoice status based on balance
- **Licence reminders:** Run `/compliance/reminders/generate` or rely on scheduled job
- **Client scoping:** CLIENT role auto-scoped; denials return 404

## Role & Panel Mapping (Who can access what)

### Payroll (`/api/v1/payroll`)
| Action | SA | AD | OM | HR | SS | AC | CL | WK | CD | Panel | UI Suggestion |
|---|---|---|---|---|---|---|---|---|---|---|---|
| List/View runs | ✅ | ✅ | ✅ | ✅ | - | ✅ | Own only | - | - | Staff/Accounts/Client | Filter by period/client |
| Create/Generate/Calculate | ✅ | ✅ | - | ✅ | - | ✅ | - | - | - | HR/Accounts | Payroll processing |
| Submit/Approve/Lock | ✅ | ✅ | - | - | - | ✅ | - | - | - | Admin/Accounts | Approval queue |
| View payslips | ✅ | ✅ | ✅ | ✅ | - | ✅ | Own only | Self* | - | Staff/Client/Worker | Payslip view/download |

### Billing (`/api/v1/invoices`)
| Action | SA | AD | OM | HR | SS | AC | CL | WK | CD | Panel | UI Suggestion |
|---|---|---|---|---|---|---|---|---|---|---|---|
| List/View invoices | ✅ | ✅ | - | - | - | ✅ | Own only | - | - | Accounts/Client | Invoice list with status/balance |
| Generate from payroll | ✅ | ✅ | - | - | - | ✅ | - | - | - | Accounts | From approved payroll run |
| Issue invoice | ✅ | ✅ | - | - | - | ✅ | - | - | - | Accounts | Issue DRAFT |
| Record payment | ✅ | ✅ | - | - | - | ✅ | - | - | - | Accounts | Payment entry |
| Credit notes | ✅ | ✅ | - | - | - | ✅ | - | - | - | Accounts | Against issued invoices |
| Cancel invoice | ✅ | ✅ | - | - | - | ✅ | - | - | - | Accounts | Only DRAFT |

### Compliance & Notifications (`/api/v1/licences`, `/api/v1/compliance`, `/api/v1/notifications`, `/api/v1/reports`)
| Action | SA | AD | OM | HR | SS | AC | CL | WK | CD | Panel | UI Suggestion |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Manage licences/challans | ✅ | ✅ | - | - | - | ✅ | - | - | - | Accounts/Compliance | Compliance dashboard |
| View reminders | ✅ | ✅ | ✅ | ✅ | - | ✅ | - | - | - | Staff | Expiry alerts widget |
| Generate reminders/notifications | ✅ | ✅ | - | - | - | ✅ | - | - | - | Accounts/Admin | Trigger reminders |
| View own notifications | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | - | All authenticated | Bell icon with count |
| View reports | ✅ | ✅ | ✅ | ✅ | - | ✅ | Own only | - | - | Staff/Client/Accounts | Reports module |

### Menu Placement Suggestions
| Panel | Suggested Menu Items |
|---|---|
| **Client Portal** | Dashboard, **Invoices** (View/Pay), **Payslips**, **Notifications** |
| **Accounts/Finance** | **Payroll** (Runs/Processing), **Billing** (Invoices/Payments/Credit Notes), **Compliance** (Licences/Challans/Reminders), **Reports** |
| **HR/Ops** | **Payroll** (Generate/Calculate), **Workers**, **Attendance**, **Compliance** (View/Manage) |
| **Admin/SA** | **Payroll**, **Billing**, **Compliance**, **Notifications**, **Reports**, **Users/Roles/Audit** |
| **Worker Portal** | **My Payslips**, **My Notifications**, **My Leave** |
