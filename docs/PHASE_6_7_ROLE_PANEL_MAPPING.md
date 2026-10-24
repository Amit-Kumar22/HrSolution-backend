# Phase 6 & 7 - Role/Panel Mapping (What to show where)

## Role Quick Reference
| Role | Code | Notes |
|---|---|---|
| SUPER_ADMIN | SA | Full access |
| ADMIN | AD | All except system settings/role editing |
| HR_RECRUITER | HR | Recruitment focused |
| OPERATIONS_MANAGER | OM | Requisitions, deployments, workforce ops |
| SITE_SUPERVISOR | SS | Field operations - attendance marking for assigned sites |
| ACCOUNTS | AC | Finance - payroll/billing later; can see sensitive data |
| CLIENT | CL | Own company only - requisitions, approvals, deployed workers |
| WORKER | WK | Self-service only |
| CANDIDATE | CD | Job seeker (separate portal) |

## Phase 6 Mapping

### Requisitions (`/requisitions`)
| Action | SA | AD | OM | HR | SS | AC | CL | WK | CD | Panel | UI Suggestion |
|---|---|---|---|---|---|---|---|---|---|---|---|
| List all requisitions | ✅ | ✅ | ✅ | ✅ | - | - | Own only | - | - | Staff Ops / Client Portal | Staff: full list with filters; Client: "My Requisitions" |
| View requisition + detail/trail | ✅ | ✅ | ✅ | ✅ | - | - | Own only | - | - | Staff Ops / Client Portal | Detail drawer with status history |
| Create requisition | ✅ | ✅ | ✅ | - | - | - | ✅ | - | - | Client Portal / Ops | Client: "Raise Requirement"; Staff: can create on client's behalf |
| Submit requisition (DRAFT->SUBMITTED) | ✅ | ✅ | ✅ | - | - | - | ✅ | - | - | Client Portal / Ops | Show "Submit for Approval" |
| Approve/Reject requisition | ✅ | ✅ | ✅ | - | - | - | - | - | - | Staff Ops (Approvals) | Approvals queue for OM/ADMIN/SA |
| Cancel requisition | ✅ | ✅ | ✅ | - | - | - | ✅ (own) | - | - | Client Portal / Ops | Cancel with confirmation |
| Urgent list | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | Ops Dashboard | "Urgent to Fill" widget |

### Workers (`/workers`)
| Action | SA | AD | OM | HR | SS | AC | CL | WK | CD | Panel | UI Suggestion |
|---|---|---|---|---|---|---|---|---|---|---|---|
| List workers | ✅ | ✅ | ✅ | ✅ | - | ✅ | - | - | - | Staff HR/Ops/Accounts | Master list with status, deployment, verification |
| View worker profile (masked) | ✅ | ✅ | ✅ | ✅ | - | ✅ | - | Self* | - | Staff/Worker Portal | Profile card |
| View sensitive (Aadhaar/PAN unmasked) | ✅ | ✅ | ✅ | - | - | ✅ | - | - | - | Staff (Restricted) | "View Sensitive" button -> audited, show with reveal |
| Create worker manually | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | HR/Ops | "Add Worker" form |
| Convert from JOINED application | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | Recruitment/HR | From Application Detail -> "Convert to Worker" |
| Update worker | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | HR/Ops | Edit profile |
| Police verification | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | HR/Ops | Status update (blocks security deployment if FAILED) |
| Exit worker (Resign/Terminate) | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | HR/Ops | Exit modal (blocks if active deployment) |
| Manage bank details | ✅ | ✅ | ✅ | ✅ | - | ✅ | - | Self* | - | HR/Ops/Accounts/Worker | Masked display; full edit by HR/Ops/Accounts |
| Manage worker documents | ✅ | ✅ | ✅ | ✅ | - | - | - | Self upload* | - | HR/Ops/Worker | Upload + verify (verify by HR/Ops) |
| Manage salary structure | ✅ | ✅ | ✅ | - | - | ✅ | - | - | - | HR/Accounts | Effective dated history |

*\*Self-service for WORKER role where applicable.*

### Deployments (`/deployments`)
| Action | SA | AD | OM | HR | SS | AC | CL | WK | CD | Panel | UI Suggestion |
|---|---|---|---|---|---|---|---|---|---|---|---|
| List deployments | ✅ | ✅ | ✅ | ✅ | - | ✅ | Own only | - | - | Staff/Client Portal | Filter by site/worker/requisition/status |
| View deployment details | ✅ | ✅ | ✅ | ✅ | - | ✅ | Own only | - | - | Staff/Client Portal | Shows client/site/worker/rates/snapshot |
| Create deployment/posting | ✅ | ✅ | ✅ | - | - | - | - | - | - | Ops | "Post Worker" (must pass overlap/security/rate checks) |
| Transfer deployment | ✅ | ✅ | ✅ | - | - | - | - | - | - | Ops | Transfer modal |
| Release deployment | ✅ | ✅ | ✅ | - | - | - | - | - | - | Ops | Release with reason (required) |
| View deployed workers at site | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Own only | - | - | Staff/Supervisor/Client | For attendance context |

## Phase 7 Mapping

### Attendance (`/attendance`)
| Action | SA | AD | OM | HR | SS | AC | CL | WK | CD | Panel | UI Suggestion |
|---|---|---|---|---|---|---|---|---|---|---|---|
| List attendance sheets | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Own only | - | - | Staff/Supervisor/Client/Accounts | Monthly sheets list by site/client |
| View sheet + entries | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Own only | - | - | All relevant | Day-wise grid (PRESENT/ABSENT/HALF_DAY/WEEK_OFF/HOLIDAY/LEAVE) |
| Generate sheet | ✅ | ✅ | ✅ | ✅ | ✅ | - | - | - | - | Ops/Supervisor | "Generate for Month" (pulls active deployments) |
| Mark/edit entries (DRAFT) | ✅ | ✅ | ✅ | ✅ | ✅ | - | - | - | - | Supervisor/Ops | Editable grid until submitted |
| Submit sheet (DRAFT->SUBMITTED) | ✅ | ✅ | ✅ | ✅ | ✅ | - | - | - | - | Supervisor/Ops | Submit for client approval |
| Approve sheet (SUBMITTED->CLIENT_APPROVED) | ✅ | ✅ | ✅ | - | - | - | ✅ | - | - | Client Portal | "Approve Attendance" with review |
| Lock sheet (CLIENT_APPROVED->LOCKED) | ✅ | ✅ | ✅ | - | - | - | - | - | - | Ops/Admin | Lock (payroll ready) - irreversible without unlock |
| Unlock sheet (LOCKED->DRAFT) | ✅ | ✅ | ✅ (with reason) | ✅ | - | - | - | - | - | Admin only | Unlock modal with **mandatory reason** (audited) |

### Leave (`/leave`)
| Action | SA | AD | OM | HR | SS | AC | CL | WK | CD | Panel | UI Suggestion |
|---|---|---|---|---|---|---|---|---|---|---|---|
| List leave requests | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | HR/Ops/Admin | Approver view with status filters |
| Apply for leave (self) | - | - | - | - | - | - | - | ✅ | - | Worker Portal | "Apply Leave" form (CASUAL/SICK/EARNED/UNPAID) |
| Approve leave | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | HR/Ops/Admin | Approve pending requests |
| Reject leave (with reason) | ✅ | ✅ | ✅ | ✅ | - | - | - | - | - | HR/Ops/Admin | Reject modal requiring reason |
| Cancel leave (self, pending) | - | - | - | - | - | - | - | ✅ | - | Worker Portal | Cancel button on pending requests |
| View own leave history | - | - | - | - | - | - | - | ✅ | - | Worker Portal | "My Leaves" tab |

### Holidays (`/holidays`)
| Action | SA | AD | OM | HR | SS | AC | CL | WK | CD | Panel | UI Suggestion |
|---|---|---|---|---|---|---|---|---|---|---|---|
| View holiday calendar | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | - | All authenticated | Calendar view (national + state-wise) |
| Add/Edit/Delete holiday | ✅ | ✅ | - | - | - | - | - | - | - | Admin Settings | Master data -> Holiday Calendar (SETTINGS_MANAGE) |

## Menu Placement Suggestions

| Panel | Suggested Menu Items |
|---|---|
| **Client Portal** | Dashboard, My Company, Sites, Contracts, Rates, **My Requisitions** (raise/track), **Deployed Workers**, **Attendance Approval** (approve sheets), Invoices (Phase 9) |
| **HR/Recruitment** | Jobs, Candidates, Applications, Interviews, **Workers** (onboard/convert/exit/documents), **Requisitions** (view/create if needed) |
| **Operations Manager** | Requisitions (approve/queue), **Deployments** (post/transfer/release), **Workers**, Sites/Clients, **Attendance** (generate/submit) |
| **Site Supervisor** | **Attendance** (mark daily/monthly for assigned sites), Workers at site, Leave (view) |
| **Accounts/Finance** | Clients, **Workers** (sensitive view if needed), **Attendance** (read/lock), Payroll (Phase 8), Billing (Phase 9) |
| **Worker Portal** | My Profile, My Documents, **My Leave** (apply/cancel), **My Attendance** (Phase 7 read), Payslips (Phase 8) |
| **Admin/SA** | Requisitions, Workers, Deployments, **Attendance** (all), **Leave Approvals**, **Holidays**, Users/Roles, Audit |

## Frontend Gotchas
- **Client scoping** - never trust `clientId` from URL for CLIENT role; always use `/me` context or let API scope
- **404 for cross-client access** - treat as "not found/not accessible" (don't show 403 style)
- **Attendance flow** - enforce DRAFT→SUBMITTED→CLIENT_APPROVED→LOCKED in UI
- **Locked sheet** - disable all edit/submit actions, show lock info + unlock reason if any
- **Sensitive data** - gate behind permission check; show masked by default
- **Deployment rules** - show errors clearly (overlap, police verification missing, no rate card)
- **Leave** - only PENDING cancellable by worker; rejection needs reason
