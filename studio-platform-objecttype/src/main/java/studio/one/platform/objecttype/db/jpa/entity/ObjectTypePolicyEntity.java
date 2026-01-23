package studio.one.platform.objecttype.db.jpa.entity;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;
import lombok.NoArgsConstructor;
import studio.one.platform.objecttype.model.ObjectPolicy;

@Data
@NoArgsConstructor
@Entity
@Table(name = "tb_application_object_type_policy")
public class ObjectTypePolicyEntity implements ObjectPolicy {

    @Id
    @Column(name = "object_type", nullable = false)
    private int objectType;

    @Column(name = "max_file_mb")
    private Integer maxFileMb;

    @Column(name = "allowed_ext")
    private String allowedExt;

    @Column(name = "allowed_mime")
    private String allowedMime;

    @Column(name = "policy_json")
    private String policyJson;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_by_id", nullable = false)
    private long createdById;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_by_id", nullable = false)
    private long updatedById;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public String getPolicyKey() {
        return "objecttype:" + objectType;
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attrs = new HashMap<>();
        if (maxFileMb != null) {
            attrs.put("maxFileMb", maxFileMb);
        }
        if (allowedExt != null) {
            attrs.put("allowedExt", allowedExt);
        }
        if (allowedMime != null) {
            attrs.put("allowedMime", allowedMime);
        }
        if (policyJson != null) {
            attrs.put("policyJson", policyJson);
        }
        return attrs.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(attrs);
    }
}
