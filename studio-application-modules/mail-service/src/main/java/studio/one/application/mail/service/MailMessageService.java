package studio.one.application.mail.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.application.mail.domain.model.MailMessage;
import studio.one.platform.constant.ServiceNames;

public interface MailMessageService {
    
    public static final String SERVICE_NAME = ServiceNames.Featrues.PREFIX  + ":mail:message-service";

    MailMessage get(long mailId);

    Optional<MailMessage> findByFolderAndUid(String folder, long uid);

    Optional<MailMessage> findByMessageId(String messageId);

    MailMessage saveOrUpdate(MailMessage message);

    Page<MailMessage> page(Pageable pageable);
}
