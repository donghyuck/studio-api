package studio.one.application.mail.service;

import java.util.List;

import studio.one.application.mail.domain.model.MailAttachment;

public interface MailAttachmentService {

    void replaceAttachments(long mailId, List<MailAttachment> attachments);

    List<MailAttachment> findByMailId(long mailId);
}
