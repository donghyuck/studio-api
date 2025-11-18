package studio.one.base.user.domain.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.base.user.constant.JpaEntityNames;

@Entity(name = JpaEntityNames.Company.ENTITY)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "TB_APPLICATION_COMPANY")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationCompany {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMPANY_ID")
    private Long companyId;

    @Column(name = "DISPLAY_NAME", nullable = false, length = 255)
    private String displayName;
    @Column(name = "NAME", nullable = false, unique = true, length = 100)
    private String name;
    @Column(name = "DOMAIN_NAME", length = 100)
    private String domainName;
    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @CreatedDate
    @Column(name = "CREATION_DATE", updatable = false)
    private Instant creationDate;

    @CreatedDate
    @Column(name = "MODIFIED_DATE")
    private Instant modifiedDate;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "TB_APPLICATION_COMPANY_PROPERTY", joinColumns = @JoinColumn(name = "COMPANY_ID"))
    @MapKeyColumn(name = "PROPERTY_NAME", length = 100)
    @Column(name = "PROPERTY_VALUE", length = 1024)
    @Builder.Default
    private Map<String, String> properties = new HashMap<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (creationDate == null)
            creationDate = now;
        if (modifiedDate == null)
            modifiedDate = now;
    }

    @PreUpdate
    void onUpdate() {
        if (modifiedDate == null)
            modifiedDate = Instant.now();
    }
}
