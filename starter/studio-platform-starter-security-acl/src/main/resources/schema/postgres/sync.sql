-- 1. promote every role into the ACL SID table:
INSERT INTO acl_sid (principal, sid)
SELECT false, upper(trim(name))
FROM tb_application_role
WHERE name IS NOT NULL AND trim(name) <> ''
ON CONFLICT (sid, principal) DO NOTHING;

-- 2. seed the ACL classes you expose via the user endpoints
INSERT INTO acl_class (class)
SELECT domain
FROM (VALUES ('user'), ('group'), ('role'), ('company')) AS t(domain)
ON CONFLICT (class) DO NOTHING;

-- 3. ensure each class has a __root__ object identity
INSERT INTO acl_object_identity (object_id_class, object_id_identity, entries_inheriting)
SELECT c.id, '__root__', true
FROM acl_class c
WHERE c.class IN ('user', 'group', 'role', 'company')
ON CONFLICT (object_id_class, object_id_identity) DO NOTHING;

-- 4. assign ACL entries (admin = mask 16, manager = mask 1)
WITH roots AS (
  SELECT c.class AS domain, oi.id AS object_id
  FROM acl_class c
  JOIN acl_object_identity oi ON oi.object_id_class = c.id
  WHERE c.class IN ('user','group','role','company')
    AND oi.object_id_identity = '__root__'
),
roles AS (
  SELECT sid.id AS sid_id, sid.sid
  FROM acl_sid sid
  WHERE sid.sid IN ('ROLE_ADMIN','ROLE_MANAGER') AND sid.principal = false
),
base AS (
  SELECT r.object_id, r.domain, ro.sid_id, ro.sid,
         CASE WHEN ro.sid = 'ROLE_ADMIN' THEN 16 ELSE 1 END AS mask
  FROM roots r
  CROSS JOIN roles ro
),
ordered AS (
  SELECT b.*,
         COALESCE(
           (
             SELECT max(ace_order) FROM acl_entry e
             WHERE e.acl_object_identity = b.object_id
           ), -1
         ) + ROW_NUMBER() OVER (PARTITION BY b.object_id ORDER BY b.sid) AS ace_order
  FROM base b
)
INSERT INTO acl_entry (acl_object_identity, ace_order, sid, mask, granting, audit_success, audit_failure)
SELECT object_id, ace_order, sid_id, mask, true, false, false
FROM ordered
ON CONFLICT (acl_object_identity, ace_order) DO NOTHING;

