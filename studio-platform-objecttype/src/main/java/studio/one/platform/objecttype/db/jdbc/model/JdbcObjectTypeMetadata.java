package studio.one.platform.objecttype.db.jdbc.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import studio.one.platform.objecttype.model.ObjectTypeMetadata;

public class JdbcObjectTypeMetadata implements ObjectTypeMetadata {

    private final ObjectTypeRow row;

    public JdbcObjectTypeMetadata(ObjectTypeRow row) {
        this.row = row;
    }

    @Override
    public int getObjectType() {
        return row.getObjectType();
    }

    @Override
    public String getKey() {
        return row.getCode();
    }

    @Override
    public String getName() {
        return row.getName();
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attrs = new HashMap<>();
        if (row.getDomain() != null) {
            attrs.put("domain", row.getDomain());
        }
        if (row.getStatus() != null) {
            attrs.put("status", row.getStatus());
        }
        if (row.getDescription() != null) {
            attrs.put("description", row.getDescription());
        }
        return attrs.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(attrs);
    }
}
