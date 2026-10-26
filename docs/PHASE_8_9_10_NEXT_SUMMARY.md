# Phase 8, 9 & 10 Summary

## Phase 8: Payroll (Complete)
### Entities & Schema
- `payroll_runs` - PayrollRun (client, period, status, totals, approval trail)
- `payroll_items` - PayrollItem (worker, deployment, earnings/deductions/net, paid days)
- `payroll_components` - PayrollComponent (payrollItem, component type/name, rate/base/amount, statutory flag)

### APIs
- `POST /api/v1/payroll/runs` - Create run
- `GET /api/v1/payroll/runs` - List runs (scoped)
- `GET /api/v1/payroll/runs/{id}` - Get run with items
- `POST /api/v1/payroll/runs/{id}/generate` - Generate items from deployments/attendance
- `POST /api/v1/payroll/runs/{id}/calculate` - Apply statutory components
- `POST /api/v1/payroll/runs/{id}/submit/approve/lock` - Workflow
- `GET /api/v1/payroll/runs/{id}/payslips` - Payslip data

### Key Features
- Configurable statutory calculations (PF/ESI/PT)
- Components store rate+base
- Locked runs immutable
- Client scoping enforced

## Phase 9: Billing & Invoicing (Complete)
### Entities & Schema
- `invoices` - Invoice (client, payrollRun, amounts, GST split, status, issue/cancel audit)
- `invoice_lines` - InvoiceLine (description, SAC, qty/rate/amount, GST)
- `payments` - Payment (invoice, amount, mode, reference, TDS)
- `credit_notes` - CreditNote (invoice, amount, reason)

### APIs
- `GET /api/v1/invoices` - List invoices
- `GET /api/v1/invoices/{id}` - Get invoice details
- `POST /api/v1/invoices/from-payroll` - Generate from approved payroll run
- `PATCH /api/v1/invoices/{id}/issue` - Issue draft invoice
- `POST /api/v1/invoices/{id}/payments` - Record payment
- `POST /api/v1/invoices/{id}/credit-notes` - Apply credit note
- `PATCH /api/v1/invoices/{id}/cancel` - Cancel draft invoice

### Key Features
- GST: CGST+SGST (same state) or IGST (interstate); requires client billing state
- Invoice numbering `INV/YYYY-YY/NNNN`
- Issued invoices immutable (corrections via credit notes)
- Payment tracking, partial/full paid status
- Permissions: `INVOICE_READ`, `INVOICE_MANAGE`, `PAYMENT_MANAGE`

## Phase 10: Compliance, Notifications, Reports (Complete)
### Entities & Schema
- `licences` - Licence (type, number, client, dates, status, reminders)
- `statutory_challans` - StatutoryChallan (PF/ESI/PT/LWF/TDS, period, paid, filed)
- `notifications` - Notification (user, title/message/type/link, read)

### APIs
- `GET /api/v1/licences` - List licences
- `POST /api/v1/licences` - Create licence
- `PUT /api/v1/licences/{id}` - Update licence
- `GET /api/v1/compliance/reminders?withinDays=` - Expiry reminders
- `POST /api/v1/compliance/reminders/generate` - Generate notifications
- `GET /api/v1/challans` - List challans
- `POST /api/v1/challans` - Create challan
- `GET /api/v1/notifications` - Unread notifications
- `GET /api/v1/notifications/unread-count` - Count
- `PATCH /api/v1/notifications/{id}/read` - Mark read
- `POST /api/v1/notifications/read-all` - Mark all read
- `GET /api/v1/reports/*` - Attendance/payroll/billing/compliance reports (placeholders)

### Key Features
- Licence expiry tracking with 60/30/7 day reminders
- In-app notifications
- Statutory challan management
- Report scaffolding
- Permissions: `COMPLIANCE_READ`, `COMPLIANCE_MANAGE`, `REPORT_VIEW`
