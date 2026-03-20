package studio.one.application.attachment.domain.entity;

import java.sql.Blob;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "TB_APPLICATION_ATTACHMENT_DATA")
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationAttachmentData {
    
    @Id 
    @Column(name = "ATTACHMENT_ID")
    private Long attachmentId;

    @Lob
    @Column(name = "ATTACHMENT_DATA")
    private Blob blob;

}
