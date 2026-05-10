INSERT INTO tb_application_object_type (
    object_type, code, name, domain, status, description,
    created_by, created_by_id, updated_by, updated_by_id
) VALUES
    (2001, 'attachment', 'Attachment', 'attachment', 'active', 'Default attachment object type',
     'system', 0, 'system', 0),
    (2101, 'post-attachment', 'Post Attachment', 'post', 'active', 'Well-known attachment type for post files',
     'system', 0, 'system', 0),
    (2102, 'mail-attachment', 'Mail Attachment', 'mail', 'active', 'Well-known attachment type for mail message files',
     'system', 0, 'system', 0),
    (2103, 'workspace-attachment', 'Workspace Attachment', 'workspace', 'active', 'Well-known attachment type for workspace files',
     'system', 0, 'system', 0),
    (2104, 'wiki-attachment', 'Wiki Attachment', 'wiki', 'active', 'Well-known attachment type for wiki page files',
     'system', 0, 'system', 0)
ON CONFLICT DO NOTHING;

INSERT INTO tb_application_object_type_policy (
    object_type, max_file_mb, allowed_ext, allowed_mime, policy_json,
    created_by, created_by_id, updated_by, updated_by_id
)
SELECT seed.object_type, seed.max_file_mb, seed.allowed_ext, seed.allowed_mime, seed.policy_json,
       seed.created_by, seed.created_by_id, seed.updated_by, seed.updated_by_id
FROM (
    VALUES
        (2001, 50, NULL, NULL, NULL::jsonb, 'system', 0, 'system', 0),
        (2101, 50, NULL, NULL, NULL::jsonb, 'system', 0, 'system', 0),
        (2102, 50, NULL, NULL, NULL::jsonb, 'system', 0, 'system', 0),
        (2103, 50, NULL, NULL, NULL::jsonb, 'system', 0, 'system', 0),
        (2104, 50, NULL, NULL, NULL::jsonb, 'system', 0, 'system', 0)
) AS seed(object_type, max_file_mb, allowed_ext, allowed_mime, policy_json,
          created_by, created_by_id, updated_by, updated_by_id)
WHERE EXISTS (
    SELECT 1
    FROM tb_application_object_type t
    WHERE t.object_type = seed.object_type
)
ON CONFLICT (object_type) DO NOTHING;
