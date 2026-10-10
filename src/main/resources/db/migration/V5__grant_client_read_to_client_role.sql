-- =====================================================================
-- V5 - Grant CLIENT_READ to the CLIENT role.
--
-- A gap in the V2 seed, found by ClientApiIT in Phase 4: the CLIENT role was
-- given REQUISITION_READ, DEPLOYMENT_READ, INVOICE_READ and the rest, but not
-- CLIENT_READ - so a client user could see their deployed workers and invoices
-- yet got a 403 reading their own company record, sites, contracts and rate
-- cards. That makes the client portal unusable.
--
-- Fixed forward in a new migration rather than by editing V2, which has already
-- been applied to the dev and production databases. Flyway validates checksums,
-- so an edited migration would stop those environments from starting.
--
-- WHY THIS IS SAFE TO GRANT
--
-- CLIENT_READ grants the capability to read client records; it does NOT decide
-- WHOSE. Every endpoint it guards passes through ClientAccessGuard, which
-- resolves the caller's own client from client_users and rejects any other id
-- with a 404:
--
--   GET /clients                      scoped to the caller's own client
--   GET /clients/me                   takes no id at all
--   GET /clients/{id}                 requireAccessTo
--   GET /clients/{id}/detail|users    requireAccessTo
--   GET /sites|contracts|rate-cards/* requireAccessTo
--
-- That layering - permission for the capability, ownership check for the scope -
-- is the model described in docs/rbac.md, and it is why a permission named
-- CLIENT_READ can be held by a client without exposing other clients.
-- =====================================================================

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'CLIENT'
  AND p.name = 'CLIENT_READ'
  -- Guard so the migration is harmless if the grant is ever added by hand.
  AND NOT EXISTS (SELECT 1
                  FROM role_permissions existing
                  WHERE existing.role_id = r.id
                    AND existing.permission_id = p.id);
