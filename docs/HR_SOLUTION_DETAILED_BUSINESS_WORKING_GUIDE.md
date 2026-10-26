# HR Solution - Detailed Business Working Guide (Hinglish)

> Is document ka maksad hai ki tum ye project kisi ko bhi bina code dekhe samjha sako ki ye system kya karta hai, kis purpose se bana hai aur har module ka kaam kya hai.

## 1. Project ka Purpose kya hai?

**HR Solution** ek *Contract Labour & Manpower Outsourcing Management System* hai. 

Matlab, jo companies manpower supply karti hain (Security Guards, Housekeeping, Skilled/Unskilled Labour) unke pura business ko ek hi system par manage karne ke liye ye banaya gaya hai.

### Kis problem ko solve karta hai?

Pehle manpower agencies ka kaam Excel, Register, WhatsApp par hota tha. Is system se ye sab centralized ho jata hai:

- Client se requirement lena
- Workers ko manage karna
- Worker ko site par post (Deploy) karna
- Attendance banana aur approve karwana
- Payroll calculate karna
- Client ko GST Invoice banana
- Payment track karna
- Licence aur Challan jaise compliance manage karna

**Simple shabd mein:** *"Ye ek Manpower Agency ka pura digital office hai."*

## 2. Overall Business Flow

Ek real example se samajh lo - Client ko 10 Security Guards chahiye.

| Step | Kya hota hai | Kaun karta hai |
|---|---|---|
| 1. | Enquiry aati hai (Requirement) | Sales/Website |
| 2. | Enquiry ko Client mein convert karte hain | Admin/HR |
| 3. | Client Requisition raise karta hai (Kitne log, kab se chahiye) | Client User |
| 4. | Requisition Approve hoti hai | Operations Manager/Admin |
| 5. | Worker ko site par Deploy/Post karte hain | Operations Team |
| 6. | Attendance mark -> Client approve -> Lock hota hai | Site Supervisor -> Client -> Ops/Admin |
| 7. | Payroll Generate/Calculate/Approve/Lock hota hai | Accounts Team |
| 8. | Payroll se Invoice banta hai, Issue hota hai, Payment record hota hai | Accounts Team |
| 9. | Compliance (Licence/Challan) track hota hai | Accounts/Compliance Team |

## 3. Module Wise Detailed Working (CRUD + Business Logic)

### 3.1 Auth, Users & Roles
**Purpose:** Kaun kya access kar sakta hai ye control karna.

| Operation | Working Detail |
|---|---|
| **Create User** | Naya user banta hai aur usko Role assign hota hai. Role par permissions depend karti hain. |
| **Read (Login/Me)** | Login par JWT token milta hai. `/me` se current user aur uske permissions pata chalti hain. |
| **Update** | Role, Status ya details change kar sakte hain. |
| **Delete (Soft Delete)** | Permanent delete nahi hota, sirf `deleted = true` mark hota hai taaki history safe rahe. |

### 3.2 Enquiry, Client, Site, Contract
**Purpose:** Naye clients ko onboard karna.

| Module | Create | Read | Update | Delete | Working Detail |
|---|---|---|---|---|---|
| **Enquiry** | Requirement aane par enquiry banti hai. | Enquiry pipeline dekhi jaati hai. | Status change - `NEW -> CONTACTED -> QUALIFIED -> CONVERTED -> CLOSED` | Soft Delete (rare) | Lead management ka kaam karta hai. |
| **Client** | Enquiry convert karke ya direct create karke client banta hai. | Client list + profile dekhi jaati hai. | Company details, GST, **Billing State Code** update hota hai. | Soft Delete | Yaha se billing ka GST decide hota hai. **Billing State Code** sabse important hai. |
| **Site** | Client ke under site create hota hai. | Site list dekhi jaati hai. | Address/Contact update hota hai. | Soft Delete | Attendance aur Deployment isi site par hota hai. |
| **Contract** | Client-Agency ka agreement record hota hai. | Contract details aur validity dekhi jaati hai. | Rate/Validity update hoti hai. | Soft Delete | `in-force?onDate=` se check hota hai ki contract valid hai ya nahi. |

### 3.3 Recruitment (Job, Candidate, Application, Interview)
**Purpose:** Jab khud hire karna ho tab use hota hai.

| Module | Create | Read | Update | Delete | Working Detail |
|---|---|---|---|---|---|
| **Job** | Job Opening create hoti hai (Post, Vacancies, Location) | Open jobs dekhi jaati hain | Status change (`OPEN/ON_HOLD/CLOSED`) | Soft Delete | Kitne log hire karne hain ye dikhata hai. |
| **Candidate** | Candidate add hota hai | Candidate master list | Personal details update | Soft Delete | Sab candidates ka record yaha hota hai. |
| **Application** | Candidate apply karta hai ya HR add karta hai | Stage wise applications dekhi jaati hain | Stage move - `APPLIED -> SHORTLISTED -> INTERVIEW -> SELECTED -> REJECTED` | Soft Delete | Har candidate ka process track hota hai. |
| **Interview** | Interview schedule hota hai (Date/Time/Interviewer) | Upcoming/Past interviews | Feedback add, Result (`CLEARED/FAILED/PENDING`) | Soft Delete | Interview ke baad decision liya jata hai. |
| **Convert to Worker** | Select/Joined candidate ko Worker mein convert karte hain | - | - | - | Ab ye candidate **Worker Pool** ka hissa ban jata hai aur Deploy hone ke liye ready hota hai. |

### 3.4 Workers, Requisitions, Deployments
**Purpose:** Manpower demand aur supply ko match karna.

#### A) Requisitions (Demand)
| Operation | Working Detail |
|---|---|
| **Create** | Client apni requirement raise karta hai. Shuruat mein `DRAFT` state hota hai. |
| **Read** | Client ko sirf apni, Staff ko sab dikhti hain. `urgent` se jaldi fill hone wali dikhti hain. |
| **Update** | Sirf `DRAFT` mein hi edit allowed hai. |
| **Submit** | `DRAFT -> SUBMITTED` (Client/Staff) |
| **Approve/Reject** | `SUBMITTED -> APPROVED` ya `REJECTED` (OM/Admin/SA). Reject par reason compulsory. |
| **Cancel** | Client apni requisition cancel kar sakta hai. |
| **Track** | `quantityDeployed` aur `quantityOutstanding` auto-calculate hote hain deployments se. |

#### B) Workers (Manpower Pool)
| Operation | Working Detail |
|---|---|
| **Create** | Manual create ya Application se Convert karke banta hai. Status shuruat mein `ON_BENCH` hota hai. |
| **Read** | List aur Profile dekhi jaati hai. **Aadhaar/PAN hamesha masked** hote hain. |
| **Read Sensitive** | Sirf `WORKER_SENSITIVE_READ` wale log unmask dekh sakte hain. Ye action **audited** hota hai. |
| **Update** | Details update karte hain. Aadhaar/PAN update par value aur hash dono update hote hain. |
| **Police Verification** | Security posting ke liye verify karte hain. `VERIFIED/NOT_REQUIRED` hona chahiye warna posting block hota hai. |
| **Exit** | Resign/Terminate mark karte hain. **Active deployment hone par exit nahi ho sakta.** |
| **Settle** | Full & Final complete hone par settle mark karte hain. |
| **Delete (Soft)** | Sirf tabhi jab kabhi deployment history na ho. |

#### C) Deployments (Posting)
| Operation | Working Detail |
|---|---|
| **Create (Post)** | Worker ko kisi site par assign karte hain (Requisition ke against ya direct). |
| **Read** | Kis worker ko kis site par, kis rate par, kab se post kiya gaya ye dekha jaata hai. |
| **Transfer** | Ek site se dusre site par shift karte hain. Purana deployment close hota hai, naya banta hai. |
| **Release** | Worker ko site se hata kar bench par bhej dete hain. **Reason compulsory** hota hai. |

**Important Rules:**
- **Overlap Check:** Ek worker par same period mein do ACTIVE deployment nahi ho sakte.
- **Rate Snap:** Deployment create hone par rate card ka rate copy hokar save hota hai. Baad mein rate change ho to bhi effect nahi padta.
- **Requisition Fulfilment:** Deployments se auto-calculate hota hai.

### 3.5 Attendance, Leave, Holidays
**Purpose:** Presence track karna aur payroll ka base banana.

#### A) Attendance
| Operation | Working Detail |
|---|---|
| **Generate** | Site+Month+Year select karke generate karte hain. Sirf us site par ACTIVE deployed workers ke entries bante hain. |
| **Mark/Edit** | `DRAFT` state mein entries mark/edit kar sakte hain (`PRESENT/ABSENT/HALF_DAY/WEEK_OFF/HOLIDAY/LEAVE`) |
| **Submit** | `DRAFT -> SUBMITTED` (Supervisor/HR) |
| **Approve** | `SUBMITTED -> CLIENT_APPROVED` (Client Portal se) |
| **Lock** | `CLIENT_APPROVED -> LOCKED` (Ops/Admin). **Lock hone ke baad kabhi edit nahi hota.** |
| **Unlock** | Sirf Admin unlock kar sakta hai, **reason dena compulsory** aur ye **audited** hota hai. |

#### B) Leave
| Operation | Working Detail |
|---|---|
| **Apply** | Worker khud apply karta hai. Overlap check hota hai (PENDING/APPROVED se conflict nahi hona chahiye) |
| **Read** | Worker apni, Approver sabki dekh sakta hai. |
| **Approve** | `PENDING -> APPROVED` (HR/Ops/Admin) |
| **Reject** | `PENDING -> REJECTED` (Reason compulsory) |
| **Cancel** | Sirf worker khud apni **PENDING** leave cancel kar sakta hai. |

#### C) Holidays
| Operation | Working Detail |
|---|---|
| **Create/Update/Delete** | Sirf `SETTINGS_MANAGE` wale log manage kar sakte hain. |
| **Read** | Sab authenticated users dekh sakte hain. State khali ho to National Holiday mana jata hai. |

### 3.6 Payroll
**Purpose:** Salary calculate karke payslip banana.

| Operation | Working Detail |
|---|---|
| **Create Run** | Client+Month+Year par Payroll Run banta hai (`DRAFT`) |
| **Generate** | **Sirf LOCKED attendance** se items generate hote hain. Paid Days attendance se aate hain. |
| **Calculate** | Statutory components apply hote hain (PF/ESI/PT). Earnings-Deductions=Net Pay calculate hota hai. |
| **Submit** | `GENERATED/CALCULATED -> SUBMITTED` |
| **Approve** | `SUBMITTED -> APPROVED` (Accounts Manager/Admin) |
| **Lock** | `APPROVED -> LOCKED`. **Ab immutable. Payslips final ho jate hain.** |
| **Payslips** | Har worker ka detailed payslip dekha jaata hai. |

### 3.7 Billing & Invoicing
**Purpose:** Client ko GST Invoice bhejna aur payment track karna.

| Operation | Working Detail |
|---|---|
| **Generate from Payroll** | Payroll Run se direct Invoice banta hai. |
| **Read** | Invoice list + Detail (Lines, GST, Payments, Credit Notes) |
| **Issue** | `DRAFT -> ISSUED`. Issue hone ke baad invoice edit nahi ho sakta. |
| **Record Payment** | Payment enter karte hain. Auto-status update hota hai - Partial par `PARTIALLY_PAID`, Full par `PAID`. |
| **Credit Note** | Sirf **ISSUED** invoice par apply hota hai (Correction ke liye) |
| **Cancel** | Sirf **DRAFT** invoice cancel ho sakta hai. |

**GST Rule:**
- Same State (Company State == Client Billing State) -> CGST + SGST
- Different State -> IGST
- Billing State Code missing -> Invoice generate nahi hoga

**Invoice No:** `INV/YYYY-YY/NNNN`

### 3.8 Compliance, Notifications, Reports, Audit
**Purpose:** Compliance track karna aur monitoring karna.

| Module | Working Detail |
|---|---|
| **Licences** | Agency/Client se related licences add/update karte hain. Expiry par days calculate hote hain. |
| **Reminders** | Expiring licences ke liye reminders generate hote hain (60/30/7 days). Notifications bante hain. |
| **Statutory Challans** | PF/ESI/PT/LWF/TDS challans track hote hain (Paid/Filed status) |
| **Notifications** | In-app notifications (Bell icon). Unread count dikhata hai, read mark kar sakte hain. |
| **Reports** | Attendance, Payroll, Billing Ageing, Compliance ke summary reports. |
| **Audit Logs** | Sensitive actions ka trail - kisne, kab, kya kiya (Invoice Issue, Attendance Unlock, Sensitive view). |

## 4. Panel Wise Kaun Kya Karega

| Panel | Role(s) | Main Responsibility | Key Menus |
|---|---|---|---|
| **Admin Panel** | SUPER_ADMIN, ADMIN | Pura system manage karna | Users/Roles, All Masters, Audit, Settings |
| **HR Panel** | HR_MANAGER | Recruitment + Workers + Leave | Jobs, Candidates, Applications, Interviews, Workers, Leave Approvals |
| **Ops Panel** | OPERATIONS_MANAGER | Manpower fulfilment | Requisitions, Deployments, Workers, Attendance (Generate/Submit) |
| **Accounts Panel** | ACCOUNTS_MANAGER | Finance & Compliance | Payroll, Billing, Licences, Challans, Reports |
| **Supervisor Panel** | SITE_SUPERVISOR | Site level work | My Sites, Workers at Site, Attendance Marking |
| **Client Portal** | CLIENT_USER | Apna kaam khud manage karna | My Requisitions, Deployed Workers, Attendance Approval, Invoices |
| **Worker Portal** | WORKER | Self Service | My Profile, My Documents, Apply Leave, My Payslips, Notifications |

## 5. End-to-End Demo Sequence (Kisi ko dikhane ke liye)

Demo dete waqt ye order follow karo:

1. **Login (Admin)** - System overview dikhao
2. **Enquiry -> Client Convert** - Naya client onboard dikhao
3. **Client Login -> Requisition Raise** - Client requirement raise karta hai
4. **Ops/Admin -> Approve Requisition** - Approval dikhao
5. **Post Worker (Deployment)** - Worker ko site par assign dikhao
6. **Attendance Generate -> Mark -> Submit -> Client Approve -> Lock** - Pura attendance flow
7. **Payroll Create -> Generate -> Calculate -> Submit -> Approve -> Lock** - Salary calculation
8. **Invoice from Payroll -> Issue -> Record Payment** - Billing flow
9. **Compliance (Licence/Challan) + Notifications** - Compliance tracking dikhao
10. **Reports** - Summary dikhao

## 6. Important Business Rules (Yaad rakho)

| Rule | Explanation |
|---|---|
| **Client Scoping** | Client sirf apna hi data dekhega. Cross-client access par 404 aata hai. |
| **Soft Delete** | Data kabhi permanently delete nahi hota, history preserve rahta hai. |
| **Rate Snap** | Deployment par rate fix ho jata hai, baad mein change ka effect nahi hota. |
| **No Overlapping Deployment** | Ek worker par same time mein 2 ACTIVE deployment nahi ho sakta. |
| **Attendance Lock** | Lock ke baad edit impossible. Unlock sirf Admin + Reason + Audit. |
| **Payroll only from LOCKED Attendance** | Bina locked attendance ke payroll generate nahi hota. |
| **Invoice Immutability** | ISSUED invoice edit nahi hoti. Correction ke liye Credit Note use hota hai. |
| **GST Validation** | Client ka Billing State Code missing ho to invoice generate nahi hota. |
| **Masked Sensitive** | Aadhaar/PAN masked hote hain, unmask sirf authorized + audited. |
| **Police Verification** | Security category ke liye police verification chahiye. |

## 7. Ek Line mein Explain karo

> **"HR Solution ek Contract Labour Manpower Agency ka Requisition se lekar GST Invoice tak ka pura business process automate karta hai. Client requirement se lekar worker deployment, attendance, payroll, billing aur compliance tak sab kuch ek hi system par manage hota hai."**
