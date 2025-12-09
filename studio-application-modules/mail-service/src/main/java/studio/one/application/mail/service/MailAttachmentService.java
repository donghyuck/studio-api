package studio.one.application.mail.service;

import java.util.List;

import studio.one.application.mail.domain.model.MailAttachment;
import studio.one.platform.constant.ServiceNames;

public interface MailAttachmentService {

    public static final String SERVICE_NAME = ServiceNames.Featrues.PREFIX  + ":mail:attachment-service";


    void replaceAttachments(long mailId, List<MailAttachment> attachments);

    List<MailAttachment> findByMailId(long mailId);
}
