package studio.one.application.attachment.domain.entity;

import java.io.InputStream;
import java.util.Date;
import java.util.Map; 
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import studio.one.application.attachment.domain.model.Attachment;

@Data
@NoArgsConstructor
@Entity
@Table(name = "TB_APPLICATION_ATTACHMENT")
public class ApplicationAttachment implements Attachment {

    @Id // tell persistence provider 'id' is primary key
    @Column(name = "ATTACHMENT_ID", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long attachmentId;

    @Column(name = "OBJECT_TYPE", nullable = false)
    private int objectType;

    @Column(name = "OBJECT_ID", nullable = false)
    private long objectId;

    @Column(name = "CONTENT_TYPE", nullable = false)
    private String contentType;

    @Column(name = "FILE_NAME", nullable = false)
    private String name;

    @Column(name = "FILE_SIZE", nullable = false)
    private long size;

    @CreatedBy
    @Column(name = "CREATED_BY", nullable = false)
    private long createdBy;

    @CreatedDate
    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "UPDATED_AT") 
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "TB_APPLICATION_ATTACHMENT_PROPERTY", joinColumns = {
            @JoinColumn(name = "ATTACHMENT_ID", referencedColumnName = "ATTACHMENT_ID") })
    @MapKeyColumn(name = "PROPERTY_NAME")
    @Column(name = "PROPERTY_VALUE")
    private Map<String, String> properties;

}