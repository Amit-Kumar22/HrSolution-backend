# HR Solution - Complete Project Demo Guide (Hinglish)

> Ye document pura project samajhne, demo dene aur frontend design karne ke liye banaya gaya hai.

## 1. Project Overview

**Project Name:** HR Solution (Manpower/Contract Labour Management System)  
**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Security (JWT), MySQL 8, Flyway, MapStruct, Lombok, Springdoc OpenAPI  
**Architecture:** REST API (modular), Role/Permission based, Client-scoped access  
**Auth:** JWT Bearer token (`Authorization: Bearer <token>`)

### Core Features
- Enquiry/Lead Management
- Recruitment (Job, Candidate, Application, Interview)
- Client & Contract Management
- Site Management
- Worker Management (with Aadhaar/PAN encryption/masking)
- Requisition & Deployment
- Attendance, Leave, Holidays
- Payroll (with statutory)
- Billing & Invoicing (GST)
- Compliance (Licences, Challans)
- Notifications, Reports, Audit

---

## 2. User Roles & Panels

System mein ye roles hain:

| Role Code | Role Name | Panel | Description |
|---|---|---|---|
| `SUPER_ADMIN` | Super Admin | Admin Panel (Full Access) | Pura system control |
| `ADMIN` | Admin | Admin Panel | Almost full access |
| `OPERATIONS_MANAGER` | Operations Manager | Ops Panel | Requisition, Deployment, Attendance oversee |
| `HR_MANAGER` | HR Manager | HR Panel | Recruitment, Worker, Leave approvals |
| `ACCOUNTS_MANAGER` | Accounts Manager | Accounts Panel | Payroll, Billing, Compliance, Reports |
| `SITE_SUPERVISOR` | Site Supervisor | Supervisor Panel | Site level attendance marking |
| `CLIENT_USER` | Client User | Client Portal | Apne company ka kaam (Requisition, Attendance approve, Invoice view) |
| `WORKER` | Worker | Worker Portal | Apna profile, leave apply, payslip view |
| `CANDIDATE` | Candidate | Candidate Portal | Apni application track karna |

> **Note:** CLIENT role ke users ko hamesha sirf apne hi client ka data dikhega. Backend auto-scope karta hai.

---

## 3. Complete Module-wise Flow & APIs

### Module 1: Auth & User Management
**Base:** `/api/v1/auth`, `/api/v1/users`

| Method | Endpoint | Permission/Role | Purpose | CRUD |
|---|---|---|---|---|
| POST | `/api/v1/auth/login` | Public | Login (email + password) - JWT token milta hai | Login |
| POST | `/api/v1/auth/refresh` | Public | Refresh token | - |
| GET | `/api/v1/auth/me` | Authenticated | Current logged in user info | Read |
| GET | `/api/v1/users` | `USER_MANAGE` | Users list | Read All |
| POST | `/api/v1/users` | `USER_MANAGE` | Create user with role/permissions | Create |
| PUT/PATCH | `/api/v1/users/{id}` | `USER_MANAGE` | Update user | Update |
| DELETE | `/api/v1/users/{id}` | `USER_MANAGE` | Soft delete user | Delete |

---

### Module 2: Enquiry, Client, Site, Contract
**Base:** `/api/v1/enquiries`, `/api/v1/clients`, `/api/v1/sites`, `/api/v1/contracts`

#### Enquiry
| Method | Endpoint | Permission | Purpose | CRUD |
|---|---|---|---|---|
| GET | `/api/v1/enquiries` | `ENQUIRY_MANAGE` | Enquiry list | Read |
| POST | `/api/v1/enquiries` | Public/Staff | Enquiry submit | Create |
| PATCH | `/api/v1/enquiries/{id}/status` | `ENQUIRY_MANAGE` | Status change (NEW→CONTACTED→QUALIFIED→CONVERTED→CLOSED) | Update |
| POST | `/api/v1/enquiries/{id}/convert` | `ENQUIRY_MANAGE` | Enquiry ko Client mein convert karna | Action |

#### Client
| Method | Endpoint | Permission | Purpose | CRUD |
|---|---|---|---|---|
| GET | `/api/v1/clients` | `CLIENT_READ` | Client list (scoped) | Read |
| GET | `/api/v1/clients/{id}` | `CLIENT_READ` | Client details | Read |
| POST | `/api/v1/clients` | `CLIENT_WRITE` | Create client | Create |
| PUT | `/api/v1/clients/{id}` | `CLIENT_WRITE` | Update client | Update |
| DELETE | `/api/v1/clients/{id}` | `CLIENT_WRITE` | Soft delete | Delete |

#### Site
| Method | Endpoint | Permission | Purpose | CRUD |
|---|---|---|---|---|
| GET | `/api/v1/sites` | `SITE_READ` | Sites list | Read |
| POST | `/api/v1/sites` | `SITE_MANAGE` | Create site (client se link) | Create |
| PUT | `/api/v1/sites/{id}` | `SITE_MANAGE` | Update site | Update |

#### Contract
| Method | Endpoint | Permission | Purpose | CRUD |
|---|---|---|---|---|
| GET | `/api/v1/clients/{clientId}/contracts` | `CONTRACT_READ` | Client ke contracts | Read |
| POST | `/api/v1/clients/{clientId}/contracts` | `CONTRACT_MANAGE` | Contract create | Create |
| PUT | `/api/v1/contracts/{id}` | `CONTRACT_MANAGE` | Update contract | Update |
| GET | `/api/v1/clients/{id}/contracts/in-force?onDate=` | `CONTRACT_READ` | Active contract check | Read |

---

### Module 3: Recruitment (Job, Candidate, Application, Interview)
**Base:** `/api/v1/jobs`, `/api/v1/candidates`, `/api/v1/applications`, `/api/v1/interviews`

| Module | Methods | Permission | Purpose |
|---|---|---|---|
| **Jobs** | GET/POST/PUT | `JOB_READ/WRITE` | Job opening create/list/update |
| **Candidates** | GET/POST/PUT | `CANDIDATE_READ/WRITE` | Candidate master |
| **Applications** | GET/POST/PATCH | `APPLICATION_READ/WRITE/MANAGE` | Apply, shortlist, reject, move stages |
| **Interviews** | GET/POST/PUT/PATCH | `INTERVIEW_MANAGE` | Schedule, feedback, result |

**Flow:** Job Post → Candidate Apply → Application Review → Interview Schedule → Interview Feedback → Select/Reject → **Convert to Worker** (if JOINED)

---

### Module 4: Workers, Requisitions, Deployments
**Base:** `/api/v1/workers`, `/api/v1/requisitions`, `/api/v1/deployments`

#### Requisitions (Client request for manpower)
| Method | Endpoint | Permission | Purpose | CRUD |
|---|---|---|---|---|
| GET | `/api/v1/requisitions` | `REQUISITION_READ` | List (Client ko sirf apna dikhega) | Read |
| GET | `/api/v1/requisitions/urgent` | `REQUISITION_READ` | Urgent (within 7 days) not filled | Read |
| GET | `/api/v1/requisitions/{id}` | `REQUISITION_READ` | Detail | Read |
| GET | `/api/v1/requisitions/{id}/detail` | `REQUISITION_READ` | With approval trail | Read |
| POST | `/api/v1/requisitions?clientId=` | `REQUISITION_CREATE` | Create as DRAFT (Client auto-resolved) | Create |
| PUT | `/api/v1/requisitions/{id}` | `REQUISITION_CREATE` | Update only DRAFT | Update |
| POST | `/api/v1/requisitions/{id}/submit` | `REQUISITION_CREATE` | DRAFT → SUBMITTED | Action |
| POST | `/api/v1/requisitions/{id}/approve` | `REQUISITION_APPROVE` | SUBMITTED → APPROVED (SA/ADMIN/OM) | Action |
| POST | `/api/v1/requisitions/{id}/reject` | `REQUISITION_APPROVE` | Reject with reason | Action |
| POST | `/api/v1/requisitions/{id}/cancel` | `REQUISITION_CREATE` | Cancel | Action |

**Status:** `DRAFT, SUBMITTED, APPROVED, REJECTED, CANCELLED, CLOSED`

#### Workers
| Method | Endpoint | Permission | Purpose | CRUD |
|---|---|---|---|---|
| GET | `/api/v1/workers` | `WORKER_READ` | Worker list | Read |
| GET | `/api/v1/workers/{id}` | `WORKER_READ` | Profile (Aadhaar/PAN masked) | Read |
| GET | `/api/v1/workers/{id}/sensitive` | `WORKER_SENSITIVE_READ` | Unmasked (AUDITED) | Read (Sensitive) |
| GET | `/api/v1/workers/find-by-aadhaar?aadhaar=` | `WORKER_READ` | Duplicate check | Read |
| POST | `/api/v1/workers` | `WORKER_WRITE` | Manual create (ON_BENCH) | Create |
| POST | `/api/v1/workers/from-application/{applicationId}` | `WORKER_WRITE` | Application se convert | Create |
| PUT | `/api/v1/workers/{id}` | `WORKER_WRITE` | Update worker | Update |
| PATCH | `/api/v1/workers/{id}/police-verification` | `WORKER_WRITE` | Police verification update | Update |
| PATCH | `/api/v1/workers/{id}/exit` | `WORKER_WRITE` | Resign/Terminate (no active deployment allowed) | Action |
| PATCH | `/api/v1/workers/{id}/settle` | `WORKER_WRITE` | Full & Final settled | Action |
| DELETE | `/api/v1/workers/{id}` | `WORKER_WRITE` | Soft delete (only no history) | Delete |

**Worker Sub-resources:**
- `/api/v1/workers/{workerId}/documents` - Upload/Verify (AADHAAR/PAN/PHOTO/POLICE_VERIFICATION etc.) `DOCUMENT_READ/UPLOAD/VERIFY`
- `/api/v1/workers/{workerId}/banks` - Bank details (encrypted/masked, primary enforced)
- `/api/v1/workers/{workerId}/salary-structures` - Effective dated salary

#### Deployments (Worker posting)
| Method | Endpoint | Permission | Purpose | CRUD |
|---|---|---|---|---|
| GET | `/api/v1/deployments` | `DEPLOYMENT_READ` | List (Client sees own only) | Read |
| GET | `/api/v1/deployments/{id}` | `DEPLOYMENT_READ` | Deployment detail (with snapped rate) | Read |
| GET | `/api/v1/deployments/worker/{workerId}/history` | `DEPLOYMENT_READ` | Worker's history (newest first) | Read |
| GET | `/api/v1/deployments/site/{siteId}/on?onDate=` | `DEPLOYMENT_READ` | Site pe kis date par kaun deployed | Read |
| POST | `/api/v1/deployments` | `DEPLOYMENT_MANAGE` | Post worker to site (overlap check, security check) | Create |
| PATCH | `/api/v1/deployments/{id}/transfer` | `DEPLOYMENT_MANAGE` | Transfer to another site | Action |
| PATCH | `/api/v1/deployments/{id}/release` | `DEPLOYMENT_MANAGE` | Release (reason required) | Action |

**Rules:** Ek worker ke paas same time pe 2 ACTIVE deployment nahi ho sakta. Security category ke liye police verification VERIFIED/NOT_REQUIRED chahiye. Rate card se rate snap hota hai.

---

### Module 5: Attendance, Leave, Holidays
**Base:** `/api/v1/attendance`, `/api/v1/leave`, `/api/v1/holidays`

#### Attendance
| Method | Endpoint | Permission | Purpose | CRUD |
|---|---|---|---|---|
| GET | `/api/v1/attendance` | `ATTENDANCE_READ` | Attendance sheets list | Read |
| GET | `/api/v1/attendance/{sheetId}` | `ATTENDANCE_READ` | Sheet with entries | Read |
| POST | `/api/v1/attendance/generate?siteId=&month=&year=` | `ATTENDANCE_MARK` | Generate sheet from active deployments | Create |
| PUT | `/api/v1/attendance/{sheetId}/entries` | `ATTENDANCE_MARK` | Bulk mark/edit entries (only DRAFT) | Update |
| PATCH | `/api/v1/attendance/{sheetId}/submit` | `ATTENDANCE_MARK` | DRAFT → SUBMITTED | Action |
| PATCH | `/api/v1/attendance/{sheetId}/approve` | `ATTENDANCE_APPROVE` | SUBMITTED → CLIENT_APPROVED (Client) | Action |
| PATCH | `/api/v1/attendance/{sheetId}/lock` | `ATTENDANCE_APPROVE` | CLIENT_APPROVED → LOCKED (Ops/Admin) - **Immutable** | Action |
| PATCH | `/api/v1/attendance/{sheetId}/unlock` | `ATTENDANCE_APPROVE` | LOCKED → DRAFT (Admin only, reason + audited) | Action |

**Sheet Status:** `DRAFT | SUBMITTED | CLIENT_APPROVED | LOCKED`  
**Entry Status:** `PRESENT | ABSENT | HALF_DAY | WEEK_OFF | HOLIDAY | LEAVE`

#### Leave
| Method | Endpoint | Permission | Purpose | CRUD |
|---|---|---|---|---|
| GET | `/api/v1/leave?workerId=&status=` | `LEAVE_APPROVE` | All leave requests (Approver view) | Read |
| POST | `/api/v1/leave/apply` | `SELF_LEAVE_MANAGE` | Worker applies leave (self) - overlap check | Create |
| PATCH | `/api/v1/leave/{leaveId}/approve` | `LEAVE_APPROVE` | PENDING → APPROVED | Action |
| PATCH | `/api/v1/leave/{leaveId}/reject` | `LEAVE_APPROVE` | PENDING → REJECTED (reason required) | Action |
| PATCH | `/api/v1/leave/{leaveId}/cancel` | `SELF_LEAVE_MANAGE` | PENDING → CANCELLED (self only) | Action |

**Status:** `PENDING | APPROVED | REJECTED | CANCELLED` | **Type:** `CASUAL | SICK | EARNED | UNPAID`

#### Holidays
| Method | Endpoint | Permission | Purpose | CRUD |
|---|---|---|---|---|
| GET | `/api/v1/holidays?from=&to=&state=` | Authenticated/Public | Holiday list (national if state null) | Read |
| POST | `/api/v1/holidays` | `SETTINGS_MANAGE` | Create holiday | Create |
| PUT | `/api/v1/holidays/{id}` | `SETTINGS_MANAGE` | Update holiday | Update |
| DELETE | `/api/v1/holidays/{id}` | `SETTINGS_MANAGE` | Delete holiday | Delete |

---

### Module 6: Payroll
**Base:** `/api/v1/payroll` (Permissions: `PAYROLL_READ/MANAGE/APPROVE`)

| Method | Endpoint | Permission | Purpose | CRUD/Action |
|---|---|---|---|---|
| GET | `/api/v1/payroll/runs` | `PAYROLL_READ` | Payroll runs list (scoped) | Read |
| GET | `/api/v1/payroll/runs/{id}` | `PAYROLL_READ` | Run detail with items+components | Read |
| POST | `/api/v1/payroll/runs` | `PAYROLL_MANAGE` | Create payroll run (Client/Month/Year) | Create |
| POST | `/api/v1/payroll/runs/{id}/generate` | `PAYROLL_MANAGE` | Generate items from **LOCKED** attendance | Action |
| POST | `/api/v1/payroll/runs/{id}/calculate` | `PAYROLL_MANAGE` | Apply statutory (PF/ESI/PT) | Action |
| POST | `/api/v1/payroll/runs/{id}/submit` | `PAYROLL_MANAGE` | DRAFT/GENERATED → SUBMITTED | Action |
| POST | `/api/v1/payroll/runs/{id}/approve` | `PAYROLL_APPROVE` | SUBMITTED → APPROVED | Action |
| POST | `/api/v1/payroll/runs/{id}/lock` | `PAYROLL_APPROVE` | APPROVED → LOCKED (**Immutable**) | Action |
| GET | `/api/v1/payroll/runs/{id}/payslips` | `PAYROLL_READ` | All payslips data | Read |

**Status Flow:** `DRAFT → GENERATED → CALCULATED → SUBMITTED → APPROVED → LOCKED`  
**Note:** Components mein rate+base store hota hai. Sirf LOCKED attendance se generate hota hai.

---

### Module 7: Billing & Invoicing
**Base:** `/api/v1/invoices` (Permissions: `INVOICE_READ/INVOICE_MANAGE/PAYMENT_MANAGE`)

| Method | Endpoint | Permission | Purpose | CRUD/Action |
|---|---|---|---|---|
| GET | `/api/v1/invoices` | `INVOICE_READ` | Invoices list (scoped) | Read |
| GET | `/api/v1/invoices/{id}` | `INVOICE_READ` | Invoice detail (lines/payments/credit notes) | Read |
| POST | `/api/v1/invoices/from-payroll` | `INVOICE_MANAGE` | Generate from payroll run | Create |
| PATCH | `/api/v1/invoices/{id}/issue` | `INVOICE_MANAGE` | DRAFT → ISSUED | Action |
| POST | `/api/v1/invoices/{id}/payments` | `PAYMENT_MANAGE` | Record payment (auto updates status) | Create(Action) |
| POST | `/api/v1/invoices/{id}/credit-notes` | `INVOICE_MANAGE` | Credit note apply (correction) | Create |
| PATCH | `/api/v1/invoices/{id}/cancel` | `INVOICE_MANAGE` | Cancel DRAFT only | Action |

**Status:** `DRAFT | ISSUED | PARTIALLY_PAID | PAID | CANCELLED`  
**Invoice No:** `INV/YYYY-YY/NNNN`  
**GST:** Same state → CGST+SGST (9%+9%), Different → IGST (18%). **Billing State missing = Error**  
**Rule:** ISSUED invoice kabhi edit nahi hoti - correction ke liye Credit Note use karo.

---

### Module 8: Compliance, Notifications, Reports, Audit
**Base:** `/api/v1/licences`, `/api/v1/compliance`, `/api/v1/challans`, `/api/v1/notifications`, `/api/v1/reports`, `/api/v1/audit`

#### Licences
| Method | Endpoint | Permission | Purpose | CRUD |
|---|---|---|---|---|
| GET | `/api/v1/licences` | `COMPLIANCE_READ` | Licences list | Read |
| POST | `/api/v1/licences` | `COMPLIANCE_MANAGE` | Create licence | Create |
| PUT | `/api/v1/licences/{id}` | `COMPLIANCE_MANAGE` | Update licence | Update |

**Types:** `CONTRACT_LABOUR|PSARA|GST|PF|ESI|SHOP_ESTABLISHMENT|TRADE|ISO` | **Status:** `ACTIVE|EXPIRED|UNDER_RENEWAL`

#### Compliance Reminders
| Method | Endpoint | Permission | Purpose | Action |
|---|---|---|---|---|
| GET | `/api/v1/compliance/reminders?withinDays=` | `COMPLIANCE_READ` | Expiring licences (level CRITICAL/HIGH/MEDIUM) | Read |
| POST | `/api/v1/compliance/reminders/generate` | `COMPLIANCE_MANAGE` | Generate notifications for all users | Action |

#### Statutory Challans
| Method | Endpoint | Permission | Purpose | CRUD |
|---|---|---|---|---|
| GET | `/api/v1/challans` | `COMPLIANCE_READ` | Challans list | Read |
| POST | `/api/v1/challans` | `COMPLIANCE_MANAGE` | Create challan (PF/ESI/PT/LWF/TDS) | Create |

#### Notifications (In-App)
| Method | Endpoint | Permission | Purpose | Action |
|---|---|---|---|---|
| GET | `/api/v1/notifications` | Authenticated | Unread notifications | Read |
| GET | `/api/v1/notifications/unread-count` | Authenticated | Count | Read |
| PATCH | `/api/v1/notifications/{id}/read` | Authenticated | Mark read | Action |
| POST | `/api/v1/notifications/read-all` | Authenticated | Mark all read | Action |

**Types:** `INFO | WARNING | ACTION_REQUIRED`

#### Reports
| Method | Endpoint | Permission | Purpose | Read |
|---|---|---|---|---|
| GET | `/api/v1/reports/attendance?month=&year=` | `REPORT_VIEW` | Attendance summary | Read |
| GET | `/api/v1/reports/payroll?month=&year=` | `REPORT_VIEW` | Payroll summary | Read |
| GET | `/api/v1/reports/billing/ageing` | `REPORT_VIEW` | Billing ageing | Read |
| GET | `/api/v1/reports/compliance` | `REPORT_VIEW` | Compliance summary | Read |

#### Audit
| Method | Endpoint | Permission | Purpose | Read |
|---|---|---|---|---|
| GET | `/api/v1/audit` | `AUDIT_VIEW` | Audit logs (entity/action/user/time) | Read |

---

## 4. Working Sequence (End-to-End Business Flow)

Yaha se samajh lo project ka real working sequence kya hai.

### Step 1: Enquiry → Client Onboarding
1. **Enquiry** aata hai (`/enquiries` POST) - website se ya manually
2. **Follow up** - Status change (`CONTACTED` → `QUALIFIED`)
3. **Convert** - `POST /enquiries/{id}/convert` se **Client** banta hai
4. **Client Setup** - Client details, GST, Billing State Code, Sites add karo
5. **Contract** create karo (`/contracts`) - valid dates, rate card type etc.

### Step 2: Manpower Requirement
1. **Client** login karke **Requisition** create karta hai (`POST /requisitions`) - DRAFT
2. **Review & Submit** - Client submit karta hai (`POST /requisitions/{id}/submit`) → SUBMITTED
3. **Approval** - Ops/Admin/SA approve karta hai (`POST /requisitions/{id}/approve`) → APPROVED
4. Ab ye requisition **fulfil** hone ke liye ready hai

### Step 3: Recruitment / Worker Availability
**Option A:** Existing Worker bench se post karo  
**Option B:** Naya hire karo
1. **Job** post karo (agar new hiring hai)
2. **Candidate** add/apply
3. **Application** process → **Interview** schedule
4. **Select** hone par **Convert to Worker** (`POST /workers/from-application/{id}`) → Worker banta hai (ON_BENCH)
5. **Worker setup** - Documents upload, Bank details, Police Verification (agar security hai)

### Step 4: Deployment (Posting)
1. **Find Worker** - Bench se suitable worker select karo
2. **Post Worker** - `POST /deployments` create karo (Requisition link kar sakte ho)
3. System check karega: **No overlapping ACTIVE deployment**, **Police verification** (security), **Rate card** resolve
4. Worker status → **DEPLOYED**, Requisition fulfilment update hota hai
5. Agar transfer chahiye to `PATCH /deployments/{id}/transfer`, release ke liye `PATCH /deployments/{id}/release`

### Step 5: Attendance
1. **Generate Sheet** - Site + Month + Year se `POST /attendance/generate` (active deployments se entries banenge)
2. **Mark Attendance** - Supervisor/HR entries mark karta hai (`PUT /attendance/{sheetId}/entries`) - DRAFT only editable
3. **Submit** - Supervisor submit karta hai → `SUBMITTED`
4. **Client Approval** - Client portal se approve karta hai → `CLIENT_APPROVED`
5. **Lock** - Ops/Admin lock karta hai → `LOCKED` (**Ab edit nahi ho sakta**)

> **Important:** Payroll sirf **LOCKED** attendance sheet se hi generate hoga.

### Step 6: Leave Management
1. **Worker** apply karta hai (`POST /leave/apply`) - dates, type, reason
2. System **overlap check** karta hai (PENDING/APPROVED se conflict nahi hona chahiye)
3. **HR/Ops/Admin** approve/reject karta hai (`approve/reject`)
4. Approved leave attendance par impact dal sakta hai (system flow par depend karta hai)

### Step 7: Payroll
1. **Create Run** - `POST /payroll/runs` (Client, Month, Year)
2. **Generate** - `POST /payroll/runs/{id}/generate` (LOCKED attendance se items banenge)
3. **Calculate** - `POST /payroll/runs/{id}/calculate` (PF/ESI/PT statutory apply)
4. **Submit** - Submit for approval
5. **Approve** - Approver approve karta hai
6. **Lock** - Lock karo (ab immutable) - Payslip ready
7. **View Payslips** - `GET /payroll/runs/{id}/payslips`

### Step 8: Billing & Invoicing
1. **Generate Invoice** - `POST /invoices/from-payroll` (PayrollRun se invoice banta hai)
2. System calculate karega: **Taxable + GST (CGST+SGST/IGST)** based on billing state
3. **Review & Issue** - Accounts team review karke `PATCH /invoices/{id}/issue` → ISSUED
4. **Record Payment** - Jab payment aaye `POST /invoices/{id}/payments` (Partial/Full)
   - Partial → `PARTIALLY_PAID`, Full → `PAID`
5. **Credit Note** (agar correction chahiye) - `POST /invoices/{id}/credit-notes` sirf **ISSUED** par
6. **Cancel** - Sirf **DRAFT** invoice cancel ho sakta hai

### Step 9: Compliance & Monitoring
1. **Licence Management** - All licences add karo with expiry dates
2. **Reminders** - `POST /compliance/reminders/generate` chalawo ya scheduled run (60/30/7 days)
3. **Notifications** - Users ko bell icon par dikhega
4. **Challans** - PF/ESI/PT/LWF/TDS challans track karo
5. **Reports** - Dashboard pe reports dekho
6. **Audit** - Sensitive actions audit logs mein record hote hain

---

## 5. Panel-wise Menu Suggestion (Frontend ke liye)

| Panel | Menu Items | APIs mostly use |
|---|---|---|
| **Super Admin / Admin** | Dashboard, Enquiries, Clients, Sites, Contracts, Jobs, Candidates, Applications, Interviews, Requisitions, Workers, Deployments, Attendance, Leave, Payroll, Billing, Licences, Challans, Users/Roles, Notifications, Reports, Audit | Sab permissions |
| **Operations Manager** | Dashboard, Requisitions (Approve Queue), Deployments, Workers, Sites, Attendance (Generate/Submit), Reports | `REQUISITION_*`, `DEPLOYMENT_*`, `WORKER_READ`, `ATTENDANCE_*` |
| **HR Manager** | Dashboard, Jobs, Candidates, Applications, Interviews, Workers, Requisitions (View), Leave Approvals, Attendance (View) | `JOB_*`, `CANDIDATE_*`, `APPLICATION_*`, `WORKER_*`, `LEAVE_APPROVE` |
| **Accounts Manager** | Dashboard, Payroll (Runs/Process), Billing (Invoices/Payments), Workers (Sensitive if needed), Attendance (View/Lock), Licences/Challans, Reports, Audit | `PAYROLL_*`, `INVOICE_*`, `PAYMENT_*`, `COMPLIANCE_*`, `REPORT_VIEW` |
| **Site Supervisor** | Dashboard, My Sites, Workers at Site, Attendance Marking, Holidays | `DEPLOYMENT_READ`, `ATTENDANCE_MARK` |
| **Client Portal** | Dashboard, My Company, My Sites, My Contracts, **My Requisitions** (Create/Submit), **Deployed Workers**, **Attendance Approval**, **Invoices** (View/Pay info), **Notifications** | `REQUISITION_CREATE/READ`, `ATTENDANCE_APPROVE`, `INVOICE_READ` (scoped) |
| **Worker Portal** | My Profile, My Documents, My Leave (Apply/Cancel), My Attendance, **My Payslips**, Notifications | `SELF_LEAVE_MANAGE`, `WORKER_READ` (self), `PAYROLL_READ` (self scoped) |

---

## 6. Important Business Rules (Demo mein dhyaan rakho)

| Rule | Explanation |
|---|---|
| **Client Scoping** | Client user ko kabhi bhi dusre client ka data nahi dikhega. Backend 404 return karta hai cross-access par (info leak rokne ke liye) |
| **Aadhaar/PAN Masking** | Default mein masked. Unmasked ke liye `WORKER_SENSITIVE_READ` + audited |
| **No Overlapping Deployment** | Ek worker ka ek se zyada ACTIVE deployment same period mein nahi ho sakta |
| **Rate Snap** | Deployment create hone par rate card se rate copy hota hai - baad mein rate card change ho to bhi deployment par effect nahi |
| **Attendance Lock** | LOCKED hone ke baad edit impossible. Unlock sirf ADMIN + reason + audit |
| **Payroll from Locked Only** | Sirf LOCKED attendance sheets se payroll generate hota hai |
| **Invoice Immutability** | ISSUED invoice edit nahi hoti - Credit Note se adjust karo |
| **GST Validation** | Billing state code missing hone par invoice generate nahi hoga |
| **Requisition Fulfilment** | Computed from deployments (DB mein store nahi hota) |
| **Police Verification** | Security category ke deployment ke liye VERIFIED/NOT_REQUIRED chahiye |
| **Soft Deletes** | Zyada tar entities soft delete hote hain (deleted flag) |
| **Audit Trail** | Sensitive operations (issue invoice, unlock attendance, view sensitive) audited hote hain |

---

## 7. Quick Demo Script (Step by Step)

Agar tum demo dena chahte ho to ye sequence follow karo:

1. **Login** as Admin → Dashboard dekhao
2. **Enquiry Convert** → Client create
3. **Client** login karo → **Requisition** create & submit
4. **Admin/Ops** login → Requisition **approve**
5. **Worker** create/convert from application → Documents/Bank add
6. **Deployments** → Worker ko site par post karo
7. **Attendance** → Generate → Mark → Submit → Client approve → Lock
8. **Payroll** → Create run → Generate → Calculate → Submit → Approve → Lock
9. **Billing** → Generate invoice from payroll → Issue → Record payment
10. **Compliance** → Add licence with near expiry → Generate reminders → Notifications check
11. **Reports** → Reports module dekhao

---

## 8. API Base Info

- **Base URL:** `/api/v1`
- **Auth:** `Authorization: Bearer <JWT>`
- **Content-Type:** `application/json`
- **Swagger:** `/swagger-ui.html` ya `/v3/api-docs`
- **Pagination:** `?page=0&size=20&sort=field,desc`
- **Client Scoping:** Automatic for CLIENT role
- **Errors:** Standard REST errors with messages

---

**Document Version:** 1.0  
**Last Updated:** Phase 1-10 Complete  
**Purpose:** Frontend Development & Project Demo
