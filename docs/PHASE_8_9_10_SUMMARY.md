# Phase 8, 9 & 10 Summary

## Phase 8: Payroll
- **Migration:** `V10__payroll_runs_items_components.sql` (payroll_runs, payroll_items, payroll_components)
- **Modules:** `payroll/` (entity, dto, mapper, repository, service, controller)
- **Features:** Configurable statutory components (PF/ESI/PT), deductions/additions per component, approval workflow, payslip data, bank/PF/ESI exports scaffolding
- **Notes:** Payroll components store rate/base; items linked to workers/deployments

## Phase 9: Billing & Invoicing
- **Migration:** `V11__invoices_payments_credit_notes.sql` (invoices, invoice_lines, payments, credit_notes)
- **Modules:** `billing/` (entity, dto, mapper, repository, service, controller)
- **Features:** Generate invoice from PayrollRun, GST (CGST+SGST if same state else IGST; refuse if missing), invoice numbering `INV/YYYY-YY/NNNN`, issue/record payments/apply credit notes, issued invoices immutable, credit notes supported
- **Permissions:** `INVOICE_READ`, `INVOICE_MANAGE`, `PAYMENT_MANAGE`

## Phase 10: Compliance, Notifications, Reports
- **Migration:** `V12__compliance_notifications.sql` (licences, statutory_challans, notifications)
- **Modules:** `compliance/`, `notification/`, `report/`
- **Features:** Licence tracking with expiry reminders (60/30/7), statutory challans, in-app notifications (read/unread), report placeholders for attendance/payroll/billing/compliance
- **Permissions:** `COMPLIANCE_READ`, `COMPLIANCE_MANAGE`, `REPORT_VIEW`
