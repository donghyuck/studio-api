package studio.one.platform.objecttype.application;

import java.util.Map;

/**
 * Reserved objectType keys for attachment owners used across application modules.
 */
public final class WellKnownAttachmentObjectTypes {

    public static final int GENERIC_ATTACHMENT = 2001;
    public static final int POST_ATTACHMENT = 2101;
    public static final int MAIL_ATTACHMENT = 2102;
    public static final int WORKSPACE_ATTACHMENT = 2103;
    public static final int WIKI_ATTACHMENT = 2104;

    public static final String KEY_GENERIC_ATTACHMENT = "attachment";
    public static final String KEY_POST_ATTACHMENT = "post-attachment";
    public static final String KEY_MAIL_ATTACHMENT = "mail-attachment";
    public static final String KEY_WORKSPACE_ATTACHMENT = "workspace-attachment";
    public static final String KEY_WIKI_ATTACHMENT = "wiki-attachment";

    private static final Map<String, Integer> TYPES_BY_KEY = Map.of(
            KEY_GENERIC_ATTACHMENT, GENERIC_ATTACHMENT,
            KEY_POST_ATTACHMENT, POST_ATTACHMENT,
            KEY_MAIL_ATTACHMENT, MAIL_ATTACHMENT,
            KEY_WORKSPACE_ATTACHMENT, WORKSPACE_ATTACHMENT,
            KEY_WIKI_ATTACHMENT, WIKI_ATTACHMENT);

    private WellKnownAttachmentObjectTypes() {
    }

    public static Map<String, Integer> typesByKey() {
        return TYPES_BY_KEY;
    }
}
