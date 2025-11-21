package studio.one.application.attachment.domain.entity;

import java.sql.Blob;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "TB_APPLICATIOIN_ATTACHMENT_DATA")
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
