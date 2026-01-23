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
import studio.one.platform.objecttype.model.ObjectTypeMetadata;

@Data
@NoArgsConstructor
@Entity
@Table(name = "tb_application_object_type")
public class ObjectTypeEntity implements ObjectTypeMetadata {

    @Id
    @Column(name = "object_type", nullable = false)
    private int objectType;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "domain", nullable = false)
    private String domain;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "description")
    private String description;

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
    public String getKey() {
        return code;
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attrs = new HashMap<>();
        if (domain != null) {
            attrs.put("domain", domain);
        }
        if (status != null) {
            attrs.put("status", status);
        }
        if (description != null) {
            attrs.put("description", description);
        }
        return attrs.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(attrs);
    }
}
