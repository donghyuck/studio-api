package studio.one.platform.objecttype.db.jdbc.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import studio.one.platform.objecttype.model.ObjectPolicy;

public class JdbcObjectPolicy implements ObjectPolicy {

    private final ObjectTypePolicyRow row;

    public JdbcObjectPolicy(ObjectTypePolicyRow row) {
        this.row = row;
    }

    @Override
    public String getPolicyKey() {
        return "objecttype:" + row.getObjectType();
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attrs = new HashMap<>();
        if (row.getMaxFileMb() != null) {
            attrs.put("maxFileMb", row.getMaxFileMb());
        }
        if (row.getAllowedExt() != null) {
            attrs.put("allowedExt", row.getAllowedExt());
        }
        if (row.getAllowedMime() != null) {
            attrs.put("allowedMime", row.getAllowedMime());
        }
        if (row.getPolicyJson() != null) {
            attrs.put("policyJson", row.getPolicyJson());
        }
        return attrs.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(attrs);
    }
}
