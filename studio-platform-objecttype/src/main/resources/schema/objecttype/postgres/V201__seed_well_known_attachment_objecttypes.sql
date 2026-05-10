INSERT INTO tb_application_object_type (
    object_type, code, name, domain, status, description,
    created_by, created_by_id, updated_by, updated_by_id
) VALUES
    (2101, 'post-attachment', 'Post Attachment', 'post', 'active', 'Well-known attachment type for post files',
     'system', 0, 'system', 0),
    (2102, 'mail-attachment', 'Mail Attachment', 'mail', 'active', 'Well-known attachment type for mail message files',
     'system', 0, 'system', 0),
    (2103, 'workspace-attachment', 'Workspace Attachment', 'workspace', 'active', 'Well-known attachment type for workspace files',
     'system', 0, 'system', 0),
    (2104, 'wiki-attachment', 'Wiki Attachment', 'wiki', 'active', 'Well-known attachment type for wiki page files',
     'system', 0, 'system', 0)
ON CONFLICT (object_type) DO NOTHING;

INSERT INTO tb_application_object_type_policy (
    object_type, max_file_mb, allowed_ext, allowed_mime, policy_json,
    created_by, created_by_id, updated_by, updated_by_id
) VALUES
    (2101, 50, NULL, NULL, NULL, 'system', 0, 'system', 0),
    (2102, 50, NULL, NULL, NULL, 'system', 0, 'system', 0),
    (2103, 50, NULL, NULL, NULL, 'system', 0, 'system', 0),
    (2104, 50, NULL, NULL, NULL, 'system', 0, 'system', 0)
ON CONFLICT (object_type) DO NOTHING;
