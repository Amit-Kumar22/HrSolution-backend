-- =====================================================================
-- V7 - Remove JOB_READ from the CANDIDATE role.
--
-- V2 granted it on the reasoning that "job adverts are public anyway". That is
-- true of the public careers endpoints, which are anonymous and need no
-- permission at all. It is not true of the staff endpoints added in Phase 6 of
-- this file's numbering - GET /api/v1/jobs and GET /api/v1/jobs/{id} - which
-- JOB_READ actually guards.
--
-- Caught by walking the candidate's journey end to end against a running
-- server. A signed-in candidate could call the staff job endpoints and read:
--
--   * clientName and clientId - which client the vacancy is for. The entire
--     reason JobDtos has separate PublicResponse and Response records is that
--     clients require vacancies not be attributed to them; this handed it over
--     through a different door.
--   * DRAFT and CLOSED adverts, i.e. roles not yet announced or already
--     withdrawn.
--   * applicationCount and openingsCommitted - how much competition there is,
--     which is the client's commercial information, not the applicant's.
--
-- Candidates lose nothing: browsing vacancies and applying both run through
-- GET /api/v1/public/jobs and POST /api/v1/public/jobs/{slug}/apply, which are
-- unauthenticated, plus the /api/v1/me endpoints gated on SELF_*.
--
-- After this, JOB_READ means "can see the internal recruitment pipeline" and is
-- held by SUPER_ADMIN, ADMIN and HR_RECRUITER only.
--
-- Fixed forward rather than by editing V2: that migration has already run
-- everywhere, and changing it would break Flyway's checksum on every existing
-- database. Same reasoning as V5.
-- =====================================================================

DELETE rp
FROM role_permissions rp
         JOIN roles r ON r.id = rp.role_id
         JOIN permissions p ON p.id = rp.permission_id
WHERE r.name = 'CANDIDATE'
  AND p.name = 'JOB_READ';
