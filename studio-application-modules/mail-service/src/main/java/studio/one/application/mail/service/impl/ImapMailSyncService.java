package studio.one.application.mail.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.mail.config.ImapProperties;
import studio.one.application.mail.domain.model.DefaultMailAttachment;
import studio.one.application.mail.domain.model.DefaultMailMessage;
import studio.one.application.mail.domain.model.MailAttachment;
import studio.one.application.mail.domain.model.MailMessage;
import studio.one.application.mail.service.MailAttachmentService;
import studio.one.application.mail.service.MailMessageService;
import studio.one.application.mail.service.MailSyncLogService;
import studio.one.application.mail.service.MailSyncService;

@Slf4j
@Service(MailSyncService.SERVICE_NAME)
public class ImapMailSyncService implements MailSyncService {

    private final ImapProperties properties;
    private final MailMessageService mailMessageService;
    private final MailAttachmentService mailAttachmentService;
    private final MailSyncLogService mailSyncLogService;

    public ImapMailSyncService(ImapProperties properties, MailMessageService mailMessageService,
            MailAttachmentService mailAttachmentService,
            MailSyncLogService mailSyncLogService) {
        this.properties = properties;
        this.mailMessageService = mailMessageService;
        this.mailAttachmentService = mailAttachmentService;
        this.mailSyncLogService = mailSyncLogService;
    }

    @Override
    public int sync() {
        var log = mailSyncLogService.start("manual");
        Properties javaMailProps = new Properties();
        String protocol = properties.getProtocol();
        javaMailProps.put("mail.store.protocol", protocol);
        javaMailProps.put("mail." + protocol + ".host", properties.getHost());
        javaMailProps.put("mail." + protocol + ".port", String.valueOf(properties.getPort()));
        if (properties.isSsl()) {
            javaMailProps.put("mail." + protocol + ".ssl.enable", "true");
        }
        Session session = Session.getInstance(javaMailProps);

        try (IMAPStore store = (IMAPStore) session.getStore(protocol)) {
            store.connect(properties.getHost(), properties.getPort(), properties.getUsername(),
                    properties.getPassword());
            IMAPFolder folder = (IMAPFolder) store.getFolder(properties.getFolder());
            folder.open(Folder.READ_ONLY);
            Message[] messages = folder.getMessages();
            FetchProfile profile = new FetchProfile();
            profile.add(FetchProfile.Item.ENVELOPE);
            profile.add(FetchProfile.Item.FLAGS);
            profile.add(FetchProfile.Item.CONTENT_INFO);
            folder.fetch(messages, profile);

            AtomicInteger processed = new AtomicInteger();
            int limit = properties.getMaxMessages() <= 0 ? messages.length : properties.getMaxMessages();
            int start = Math.max(0, messages.length - limit);
            int poolSize = Math.max(1, properties.getConcurrency());
            ExecutorService executor = Executors.newFixedThreadPool(poolSize);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = start; i < messages.length; i++) {
                Message msg = messages[i];
                futures.add(executor.submit(() -> {
                    try {
                        long uid = folder.getUID(msg);
                        MailMessage target = mailMessageService.findByFolderAndUid(properties.getFolder(), uid)
                                .orElseGet(DefaultMailMessage::new);
                        List<MailAttachment> attachments = new ArrayList<>();
                        populateMessage(target, msg, uid, attachments);
                        MailMessage saved = mailMessageService.saveOrUpdate(target);
                        mailAttachmentService.replaceAttachments(saved.getMailId(), attachments);
                        processed.incrementAndGet();
                    } catch (Exception ex) {
                        throw new IllegalStateException("Failed to process message: " + ex.getMessage(), ex);
                    }
                }));
            }
            // wait all
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (CancellationException | InterruptedException | ExecutionException ex) {
                    executor.shutdownNow();
                    mailSyncLogService.complete(log.getLogId(), processed.get(), processed.get(), 1, "failed",
                            ex.getMessage());
                    throw new IllegalStateException("Failed to complete IMAP sync: " + ex.getMessage(), ex);
                }
            }
            executor.shutdown();
            folder.close(false);
            mailSyncLogService.complete(log.getLogId(), processed.get(), processed.get(), 0, "completed", null);
            return processed.get();
        } catch (MessagingException ex) {
            mailSyncLogService.complete(log.getLogId(), 0, 0, 1, "failed", ex.getMessage());
            throw new IllegalStateException("Failed to sync IMAP messages: " + ex.getMessage(), ex);
        }
    }

    private void populateMessage(MailMessage target, Message message, long uid, List<MailAttachment> attachments)
            throws MessagingException, IOException {
        target.setFolder(properties.getFolder());
        target.setUid(uid);
        target.setMessageId(extractMessageId(message));
        target.setSubject(message.getSubject());
        target.setFromAddress(joinAddresses(message.getFrom()));
        target.setToAddress(joinAddresses(message.getRecipients(Message.RecipientType.TO)));
        target.setCcAddress(joinAddresses(message.getRecipients(Message.RecipientType.CC)));
        target.setBccAddress(joinAddresses(message.getRecipients(Message.RecipientType.BCC)));
        target.setSentAt(toInstant(message.getSentDate()));
        target.setReceivedAt(toInstant(message.getReceivedDate()));
        target.setFlags(toFlagString(message.getFlags()));
        target.setBody(extractBody(message, attachments));
        if (target.getCreatedAt() == null) {
            target.setCreatedAt(Instant.now());
        }
        target.setUpdatedAt(Instant.now());
    }

    private String joinAddresses(jakarta.mail.Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        return Arrays.stream(addresses)
                .filter(a -> a instanceof InternetAddress)
                .map(a -> (InternetAddress) a)
                .map(InternetAddress::toUnicodeString)
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
    }

    private Instant toInstant(java.util.Date date) {
        return date == null ? null : date.toInstant();
    }

    private String toFlagString(Flags flags) {
        if (flags == null) {
            return null;
        }
        return Arrays.toString(flags.getSystemFlags());
    }

    private String extractMessageId(Message message) throws MessagingException {
        if (message instanceof MimeMessage mime) {
            return mime.getMessageID();
        }
        String[] headers = message.getHeader("Message-ID");
        if (headers != null && headers.length > 0) {
            return headers[0];
        }
        return null;
    }

    private String extractBody(Message message, List<MailAttachment> attachments)
            throws IOException, MessagingException {
        Object content = message.getContent();
        if (content instanceof String) {
            return truncateBody((String) content);
        }
        if (content instanceof jakarta.mail.Multipart multipart) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                var bodyPart = multipart.getBodyPart(i);
                String disposition = bodyPart.getDisposition();
                boolean isAttachment = disposition != null
                        && disposition.equalsIgnoreCase(jakarta.mail.Part.ATTACHMENT);
                Object partContent = bodyPart.getContent();
                if (isAttachment || bodyPart.getFileName() != null) {
                    MailAttachment attachment = new DefaultMailAttachment();
                    attachment.setFilename(decodeFilename(bodyPart.getFileName()));
                    attachment.setContentType(bodyPart.getContentType());
                    byte[] bytes = toBytes(bodyPart.getInputStream(), properties.getMaxAttachmentBytes());
                    if (bytes == null) {
                        log.warn("Skip attachment '{}' exceeding max size {} bytes", attachment.getFilename(),
                                properties.getMaxAttachmentBytes());
                        continue;
                    }
                    attachment.setContent(bytes);
                    attachment.setSize(attachment.getContent() != null ? attachment.getContent().length : 0);
                    attachment.setCreatedAt(Instant.now());
                    attachments.add(attachment);
                } else if (partContent instanceof String) {
                    appendBodyPart(builder, (String) partContent);
                }
            }
            return builder.toString();
        }
        return content != null ? truncateBody(content.toString()) : null;
    }

    private String decodeFilename(String name) {
        if (name == null) {
            return null;
        }
        try {
            return MimeUtility.decodeText(name);
        } catch (Exception ex) {
            return name;
        }
    }

    private byte[] toBytes(InputStream input, long maxBytes) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    return null; // signal oversized
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private void appendBodyPart(StringBuilder builder, String partContent) {
        if (partContent == null) {
            return;
        }
        String truncated = truncateBody(partContent);
        int remaining = (int) Math.max(0, properties.getMaxBodyBytes() - builder.length());
        if (remaining <= 0) {
            return;
        }
        if (truncated.length() > remaining) {
            builder.append(truncated, 0, remaining);
        } else {
            builder.append(truncated);
        }
    }

    private String truncateBody(String body) {
        if (body == null) {
            return null;
        }
        if (body.length() > properties.getMaxBodyBytes()) {
            return body.substring(0, (int) properties.getMaxBodyBytes());
        }
        return body;
    }
}
