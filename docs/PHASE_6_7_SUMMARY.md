# Phase 6 & 7 Summary (For Frontend)

## Phase 6: Workers, Requisitions, Deployments, Encrypted Documents

### Requisitions
**Base:** `/api/v1/requisitions` (Auth required, `REQUISITION_READ/CREATE/APPROVE`)

| Method | Endpoint | Permission | Notes |
|---|---|---|---|
| GET | `/api/v1/requisitions` | `REQUISITION_READ` | List requisitions (client users see only their own; `clientId` ignored for CLIENT role) |
| GET | `/api/v1/requisitions/urgent` | `REQUISITION_READ` | Committed & starting soon not yet filled (default `withinDays=7`) |
| GET | `/api/v1/requisitions/{id}` | `REQUISITION_READ` | Get requisition details |
| GET | `/api/v1/requisitions/{id}/detail` | `REQUISITION_READ` | With approval trail (oldest first) |
| POST | `/api/v1/requisitions?clientId=` | `REQUISITION_CREATE` | Create as DRAFT (client auto-resolved for CLIENT role) |
| PUT | `/api/v1/requisitions/{id}` | `REQUISITION_CREATE` | Update DRAFT only |
| POST | `/api/v1/requisitions/{id}/submit` | `REQUISITION_CREATE` | Submit for approval |
| POST | `/api/v1/requisitions/{id}/approve` | `REQUISITION_APPROVE` | Approve (SUPER_ADMIN/ADMIN/OPERATIONS_MANAGER) |
| POST | `/api/v1/requisitions/{id}/reject` | `REQUISITION_APPROVE` | Reject (requires reason) |
| POST | `/api/v1/requisitions/{id}/cancel` | `REQUISITION_CREATE` | Cancel (CLIENT can cancel their own) |

**Key:** `siteId` mandatory (affects minimum wage/PT). `quantityDeployed`/`quantityOutstanding` are computed from deployments. Statuses: `DRAFT, SUBMITTED, APPROVED, REJECTED, CANCELLED, CLOSED`.

### Workers
**Base:** `/api/v1/workers` (Auth required, `WORKER_READ/WRITE`, sensitive needs `WORKER_SENSITIVE_READ`)

| Method | Endpoint | Permission | Notes |
|---|---|---|---|
| GET | `/api/v1/workers` | `WORKER_READ` | List with filters/search |
| GET | `/api/v1/workers/{id}` | `WORKER_READ` | Worker profile (Aadhaar/PAN masked) |
| GET | `/api/v1/workers/{id}/sensitive` | `WORKER_SENSITIVE_READ` | Unmasked Aadhaar/PAN (audited) |
| GET | `/api/v1/workers/find-by-aadhaar?aadhaar=` | `WORKER_READ` | Find existing worker by Aadhaar |
| POST | `/api/v1/workers` | `WORKER_WRITE` | Create worker manually (starts ON_BENCH) |
| POST | `/api/v1/workers/from-application/{applicationId}` | `WORKER_WRITE` | Convert JOINED application to worker |
| PUT | `/api/v1/workers/{id}` | `WORKER_WRITE` | Update worker (updating Aadhaar/PAN replaces both value + hash) |
| PATCH | `/api/vtags/` | - | See below |
| PATCH | `/api/v1/workers/{id}/police-verification` | `WORKER_WRITE` | Record police verification (affects security deployment) |
| PATCH | `/api/v1/workers/{id}/exit` | `WORKER_WRITE` | RESIGNED/TERMINATED (must not have active deployment) |
| PATCH | `/api/v1/workers/{id}/settle` | `WORKER_WRITE` | Mark full & final settled |
| DELETE | `/api/v1/workers/{id}` | `WORKER_WRITE` | Soft delete only if no deployment history |

**Worker Documents:** `/api/v1/workers/{workerId}/documents` (`DOCUMENT_READ/UPLOAD/VERIFY`) - supports AADHAAR/PAN/PHOTO/POLICE_VERIFICATION/EDUCATION/EXPERIENCE/MEDICAL. Verification tracked separately.  
**Worker Banks:** `/api/v1/workers/{workerId}/banks` - account number encrypted/masked; `primaryAccount` enforced by service.  
**Salary Structures:** `/api/v1/workers/{workerId}/salary-structures` - effective-dated, dated history.

### Deployments
**Base:** `/api/v1/deployments` (Auth required, `DEPLOYMENT_READ/MANAGE`)

| Method | Endpoint | Permission | Notes |
|---|---|---|---|
| GET | `/api/v1/deployments` | `DEPLOYMENT_READ` | List (client-scoped; CLIENT sees only their own) |
| GET | `/api/v1/deployments/{id}` | `DEPLOYMENT_READ` | Deployment detail |
| GET | `/api/v1/deployments/worker/{workerId}/history` | `DEPLOYMENT_READ` | Worker's posting history (newest first) |
| GET | `/api/v1/deployments/site/{siteId}/on?onDate=` | `DEPLOYMENT_READ` | Workers at site on date (client-facing shape) |
| POST | `/api/v1/deployments` | `DEPLOYMENT_MANAGE` | Post worker to site (against requisition optional) |
| PATCH | `/api/v1/deployments/{id}/transfer` | `DEPLOYMENT_MANAGE` | Transfer to another site/deployment context |
| PATCH | `/api/v1/dedeployments{id}/release` | `DEPLOYMENT_MANAGE` | Release worker (requires reason) |

**Critical rules:** No overlapping ACTIVE deployments for a worker (null `endDate` = open-ended). Security category requires police verification `VERIFIED/NOT_REQUIRED`. Rate card resolved by (category+site/client) on start date and **snapshotted**. Posting updates worker status (ON_BENCH<->DEPLOYED) and requisition fulfilment.

## Phase 7: Attendance, Leave, Holidays

### Attendance
**Base:** `/api/v1/attendance` (Auth required, `ATTENDANCE_READ/MARK/APPROVE`)

| Method | Endpoint | Permission | Notes |
|---|---|---|---|
| GET | `/api/v1/attendance` | `ATTENDATION READ` | List sheets (client-scoped) |
| GET | `/api/v1/attendance/{sheetId}` | `ATTENDANCE_READ` | Sheet with all entries |
| POST | `/api/v1/attendance/generate?siteId=&month=&year=` | `ATTENDANCE_MARK` | Generate from active deployments (auto-creates entries) |
| PUT | `/api/v1/attendance/{sheetId}/entries` | `ATTENDANCE_MARK` | Bulk update entries (only DRAFT editable) |
| PATCH | `/api/v1/attendance/{sheetId}/submit` | `ATTENDANCE_MARK` | Submit for client approval (DRAFT->SUBMITTED) |
| PATCH | `/api/v1/attendance/{sheetId}/approve` | `ATTENDANCE_APPROVE` | Client approval (SUBMITTED->CLIENT_APPROVED) |
| PATCH | `/api/v1/attendance/{sheetId}/lock` | `ATTENDANCE_APPROVE` | Lock (CLIENT_APPROVED->LOCKED). Immutable after lock |
| PATCH | `/api/v1/attendance/{sheetId}/unlock` | `ATTENDANCE_APPROVE` | Unlock with reason (LOCKED->DRAFT). **Audited**. Payroll consumes locked sheets. |

**Sheet statuses:** `DRAFT | SUBMITTED | CLIENT_APPROVED | LOCKED`  
**Entry statuses:** `PRESENT | ABSENT | HALF_DAY | WEEK_OFF | HOLIDAY | LEAVE`

### Leave
**Base:** `/api/v1/leave` (Auth required)

| Method & Endpoints | Permission & Notes |
|---|---|
| GET `/api/v1/leave?workerId=&status=` | `LEAVE_APPROVE` - List all leave requests (approver view) |
| POST `/api/v1/leave/apply` | `SELF_LEAVE_MANAGE` - Worker applies for leave (self only). Checks overlap with PENDING/APPROVED. |
| PATCH `/api/v1/leave/{leaveId}/approve` | `LEAVE_APPROVE` - Approve pending request (HR/OM/Admin/Supervisor depending on org flow) |
| PATCH `/api/v1/leave/{leaveId}/reject` | `LEAVE_APPROVE` - Reject pending (requires rejectionReason) |
| PATCH `/api/v1/leave/{leaveId}/cancel` | `SELF_LEAVE_MANAGE` - Worker cancels pending request only |

**Leave statuses:** `PENDING | APPROVED | REKEJCTED | CANCELLED`  
**Leave types:** `CASUAL | SICK | EARNED | UNPAID`

### Holidays
**Base:** `/api/v1/holidays`

| Method & Endpoints | Permission & Notes |
|---|---|
| GET `/api/v1/holidays?from=&to=&state=` | Public/Authenticated - Filter by date range and state (national if state null) |
| POST / PUT / DELETE `/api/v1/holidays[/{id}]` | `SETTINGS_MANAGE` - Manage holiday calendar (admin only) |

## Important Notes for Frontend

- **Client scoping:** CLIENT role users are auto-scoped to their own company; any `clientId` passed is ignored/overridden. Denials return 404 (not 403) to avoid enumeration.
- **Ownership:** Backend always enforces ownership - UI hiding is cosmetic.
- **Encrypted fields:** Aadhaar/PAN/bank account masked by default. Use sensitive endpoints only if user has `WORKER_SENSITIVE_READ` (audited).
- **Attendance locking:** Once LOCKED, sheet cannot be edited. Unlock requires reason and is audited (affects payroll).
- **Rate cards:** Never edited in place - deployment uses snapped rate from applicable rate card.
- **Requisitions:** Fulfilment computed from deployments, not stored.
- **No overlapping deployments:** Posting will fail if worker has overlapping ACTIVE deployment.


## Role & Panel Mapping (Who can access what)

### Phase 6 Mapping

#### Requisitions (`/api/v1/requisitions`)
| Action | SA | AD | OM | HR | SS | AC | CL | WK | CD | Panel | UI Suggestion |
|---|---|---|---|---|---|---|---|---|---|---|---|
| List requisitions | ✅ | ✅ | ✅ | ✅ | - | - | Own only | - | - | Staff Ops / Client Portal | Staff: full list with filters; Client: "My Requisitions" |
| View requisition + detail/trail | ✅ | ✅ | ✅ | ✅ | - | - | Own only | - | - | Staff Ops / Client Portal | Detail drawer with status history |
| Create requisition | ✅ | ✅ | ✅ | - | - | - | ✅ | - | - | Client Portal / Ops | Client: "Raise Requirement"; Staff: can create on client's behalf |
| Submit requisition (DRAFT->SUBMITTED) | ✅ | ✅ | ✅ | - | - | - | ✅ | - | - | Client Portal / Ops | Show "Submit for Approval" |
| Approve/Reject requisition | ✅ | ✅ | ✅ | - | - | - | - | - | - | Staff Ops (Approvals) | Approvals queue for OM/ADMIN/SA |
| Cancel requisition | ✅ | ✅ | ✅ | - | - | - | ✅ (own) | - | - | Client Portal / Ops | Cancel with confirmation |
| Urgent list | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | Ops Dashboard | "Urgent to Fill" widget |

#### Workers (`/api/v1/workers`)
| Action | SA | AD | OM | HR | SS | AC | CL | WK | CD | Panel | UI Suggestion |
|---|---|---|---|---|---|---|---|---|---|---|---|
| List workers | ✅ | ✅ | ✅ | ✅ | - | ✅ | - | - | - | Staff HR/Ops/Accounts | Master list with status, deployment, verification |
| View worker profile (masked) | ✅ | ✅ | ✅ | ✅ | - | ✅ | - | Self* | - | Staff/Worker Portal | Profile card |
| View sensitive (Aadhaar/PAN unmasked) | ✅ | ✅ | ✅ | - | - | ✅ | - | - | - | Staff (Restricted) | "View Sensitive" -> audited, reveal masked value |
| Create worker manually | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | HR/Ops | "Add Worker" form |
| Convert from JOINED application | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | Recruitment/HR | From Application Detail -> "Convert to Worker" |
| Update worker | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | HR/Ops | Edit profile |
| Police verification | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | HR/Ops | Affects security deployment (FAILED blocks) |
| Exit worker (Resign/Terminate) | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | HR/Ops | Exit modal (blocked if active deployment) |
| Manage bank details | ✅ | ✅ | ✅ | ✅ | - | ✅ | - | Self* | - | HR/Ops/Accounts/Worker | Masked by default |
| Manage worker documents | ✅ | ✅ | ✅ | ✅ | - | - | - | Self* | - | HR/Ops/Worker | Upload + verify |
| Manage salary structure | ✅ | ✅ | ✅ | - | - | ✅ | - | - | - | HR/Accounts | Effective dated history |

#### Deployments (`/api/v1/deployments`)
| Action | SA | AD | OM | HR | SS | AC | CL | WK | CD | Panel | UI Suggestion |
|---|---|---|---|---|---|---|---|---|---|---|---|
| List deployments | ✅ | ✅ | ✅ | ✅ | - | ✅ | Own only | - | - | Staff/Client Portal | Filter by site/worker/requisition/status |
| View deployment details | ✅ | ✅ | ✅ | ✅ | - | ✅ | Own only | - | - | Staff/Client Portal | Shows snapshot rates |
| Create deployment/posting | ✅ | ✅ | ✅ | - | - | - | - | - | - | Ops | "Post Worker" (checks overlap/security/rate card) |
| Transfer deployment | ✅ | ✅ | ✅ | - | - | - | - | - | - | Ops | Transfer modal |
| Release deployment | ✅ | ✅ | ✅ | - | - | - | - | - | - | Ops | Release with reason |
| View deployed workers at site | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Own only | - | - | Staff/Supervisor/Client | For attendance context |

### Phase 7 Mapping

#### Attendance (`/api/v1/attendance`)
| Action | SA | AD | OM | HR | SS | AC | CL | WK | CD | Panel | UI Suggestion |
|---|---|---|---|---|---|---|---|---|---|---|---|
| List attendance sheets | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Own only | - | - | Staff/Supervisor/Client/Accounts | By site/month/year |
| View sheet + entries | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Own only | - | - | All relevant | Day-wise grid |
| Generate sheet | ✅ | ✅ | ✅ | ✅ | ✅ | - | - | - | - | Ops/Supervisor | From active deployments |
| Mark/edit entries (DRAFT) | ✅ | ✅ | ✅ | ✅ | ✅ | - | - | - | - | Supervisor/Ops | Editable grid |
| Submit sheet (DRAFT->SUBMITTED) | ✅ | ✅ | ✅ | ✅ | ✅ | - | - | - | - | Supervisor/Ops | Send for client approval |
| Approve sheet (SUBMITTED->CLIENT_APPROVED) | ✅ | ✅ | ✅ | - | - | - | ✅ | - | - | Client Portal | Review & approve |
| Lock sheet (CLIENT_APPROVED->LOCKED) | ✅ | ✅ | ✅ | - | - | - | - | - | - | Ops/Admin | Lock for payroll (immutable) |
| Unlock sheet (LOCKED->DRAFT) | ✅ | ✅ | - | - | - | - | - | - | - | Admin only | Requires reason, audited |

#### Leave (`/api/v1/leave`)
| Action | SA | AD | OM | HR | SS | AC | CL | WK | CD | Panel | UI Suggestion |
|---|---|---|---|---|---|---|---|---|---|---|---|
| List leave requests | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | HR/Ops/Admin | Approver queue |
| Apply for leave (self) | - | - | - | - | - | - | - | ✅ | - | Worker Portal | Apply with dates/type/reason |
| Approve leave | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | HR/Ops/Admin | Approve pending |
| Reject leave (with reason) | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | HR/Ops/Admin | Reason required |
| Cancel leave (self, pending) | - | - | - | - | - | - | - | ✅ | - | Worker Portal | Cancel pending only |
| View own leave history | - | - | - | - | - | - | - | ✅ | - | Worker Portal | "My Leaves" |

#### Holidays (`/api/v1/holidays`)
| Action | SA | AD | OM | HR | SS | AC | CL | WK | CD | Panel | UI Suggestion |
|---|---|---|---|---|---|---|---|---|---|---|---|
| View holiday calendar | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | - | All authenticated | Calendar (national + state) |
| Add/Edit/Delete holiday | ✅ | ✅ | - | - | - | - | - | - | - | Admin Settings | SETTINGS_MANAGE required |

### Menu Placement Suggestions
| Panel | Suggested Menu Items |
|---|---|
| **Client Portal** | Dashboard, My Company, Sites, Contracts, Rates, **My Requisitions**, **Deployed Workers**, **Attendance Approval** |
| **HR/Recruitment** | Jobs, Candidates, Applications, Interviews, **Workers**, **Requisitions** |
| **Operations Manager** | **Requisitions** (Approve/Queue), **Deployments**, **Workers**, Sites/Clients, **Attendance** (Generate/Submit) |
| **Site Supervisor** | **Attendance** (Mark for assigned sites), Workers at site |
| **Accounts/Finance** | Clients, **Workers** (Sensitive if allowed), **Attendance** (Read/Lock), Payroll (Phase 8), Billing (Phase 9) |
| **Worker Portal** | My Profile, My Documents, **My Leave**, **My Attendance**, Payslips (Phase 8) |
| **Admin/SA** | Requisitions, Workers, Deployments, **Attendance**, **Leave Approvals**, **Holidays**, Users/Roles, Audit |

*\*Self-service applicable as per permission.*
