package studio.one.application.mail.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Builder;
import lombok.Value;
import studio.one.application.mail.domain.model.MailMessage;

@Value
@Builder
public class MailMessageDto {
    long mailId;
    String folder;
    long uid;
    String messageId;
    String subject;
    String fromAddress;
    String toAddress;
    String ccAddress;
    String bccAddress;
    Instant sentAt;
    Instant receivedAt;
    String flags;
    String body;
    Map<String, String> properties;
    List<MailAttachmentDto> attachments;

    public static MailMessageDto from(MailMessage message, List<MailAttachmentDto> attachments) {
        return MailMessageDto.builder()
                .mailId(message.getMailId())
                .folder(message.getFolder())
                .uid(message.getUid())
                .messageId(message.getMessageId())
                .subject(message.getSubject())
                .fromAddress(message.getFromAddress())
                .toAddress(message.getToAddress())
                .ccAddress(message.getCcAddress())
                .bccAddress(message.getBccAddress())
                .sentAt(message.getSentAt())
                .receivedAt(message.getReceivedAt())
                .flags(message.getFlags())
                .body(message.getBody())
                .properties(message.getProperties())
                .attachments(attachments)
                .build();
    }

    public static MailMessageDto from(MailMessage message,
            java.util.function.Supplier<List<? extends studio.one.application.mail.domain.model.MailAttachment>> attachmentSupplier) {
        List<MailAttachmentDto> attDtos = attachmentSupplier.get().stream()
                .map(MailAttachmentDto::from)
                .collect(Collectors.toList());
        return from(message, attDtos);
    }
}
