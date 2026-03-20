package studio.one.application.template.persistence.jpa.entity;

import java.time.Instant;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Data;
import lombok.NoArgsConstructor;
import studio.one.application.template.domain.model.Template;

@Data
@NoArgsConstructor
@Entity
@Table(name = "TB_APPLICATION_TEMPLATE")
@EntityListeners(AuditingEntityListener.class)
public class TemplateEntity implements Template {

    @Id
    @Column(name = "TEMPLATE_ID", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long templateId;

    @Column(name = "OBJECT_TYPE", nullable = false)
    private int objectType;

    @Column(name = "OBJECT_ID", nullable = false)
    private long objectId;

    @Column(name = "NAME", nullable = false, unique = true)
    private String name;

    @Column(name = "DISPLAY_NAME")
    private String displayName;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "SUBJECT")
    private String subject;

    @Column(name = "BODY")
    private String body;

    @CreatedBy
    @Column(name = "CREATED_BY", nullable = false)
    private long createdBy;

    @LastModifiedDate
    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @CreatedDate
    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_BY")
    private long updatedBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "TB_APPLICATION_TEMPLATE_PROPERTY", joinColumns = {
            @JoinColumn(name = "TEMPLATE_ID", referencedColumnName = "TEMPLATE_ID") })
    @MapKeyColumn(name = "PROPERTY_NAME")
    @Column(name = "PROPERTY_VALUE")
    private Map<String, String> properties;
}
