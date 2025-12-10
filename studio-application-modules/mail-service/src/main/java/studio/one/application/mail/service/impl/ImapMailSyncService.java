/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file ImapMailSyncService.java
 *      @date 2025
 *
 */

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.mail.AuthenticationFailedException;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

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
import studio.one.platform.error.ErrorType;
import studio.one.platform.error.Severity;
import studio.one.platform.exception.PlatformRuntimeException;
import studio.one.platform.exception.UnAuthorizedException;

/**
 *
 * @author  donghyuck, son
 * @since 2025-12-10
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-12-10  donghyuck, son: 최초 생성.
 * </pre>
 */

@Slf4j
@Service(MailSyncService.SERVICE_NAME)
public class ImapMailSyncService implements MailSyncService {

    private static final ErrorType SYNC_IN_PROGRESS = ErrorType.of("error.mail.sync.in-progress", HttpStatus.CONFLICT, Severity.WARN);

    private final ImapProperties properties;
    private final MailMessageService mailMessageService;
    private final MailAttachmentService mailAttachmentService;
    private final MailSyncLogService mailSyncLogService;
    private final AtomicBoolean running = new AtomicBoolean(false);

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
        var syncLog = mailSyncLogService.start("manual");
        return sync(syncLog);
    }

    @Override
    public int sync(studio.one.application.mail.domain.model.MailSyncLog syncLog) {
        if (!running.compareAndSet(false, true)) {
            throw PlatformRuntimeException.of(SYNC_IN_PROGRESS);
        }
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
            store.connect(properties.getHost(), properties.getPort(), properties.getUsername(), properties.getPassword());
            IMAPFolder folder = (IMAPFolder) store.getFolder(properties.getFolder());
            folder.open(properties.isDeleteAfterFetch() ? Folder.READ_WRITE : Folder.READ_ONLY);
            Message[] messages = folder.getMessages();
            FetchProfile profile = new FetchProfile();
            profile.add(FetchProfile.Item.ENVELOPE);
            profile.add(FetchProfile.Item.FLAGS);
            profile.add(FetchProfile.Item.CONTENT_INFO);
            folder.fetch(messages, profile);

            AtomicInteger succeeded = new AtomicInteger();
            AtomicInteger failed = new AtomicInteger();
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
                        try {
                            MailMessage saved = mailMessageService.saveOrUpdate(target);
                            mailAttachmentService.replaceAttachments(saved.getMailId(), attachments);
                            if (properties.isDeleteAfterFetch()) {
                                msg.setFlag(javax.mail.Flags.Flag.DELETED, true);
                            }
                            succeeded.incrementAndGet();
                        } catch (DataIntegrityViolationException dive) {
                            // 이미 동일 UID가 저장된 경우 등 제약 위반 → skip
                            failed.incrementAndGet();
                            this.log.warn("Skip duplicate mail (folder={}, uid={}): {}", properties.getFolder(), uid,
                                    dive.getMostSpecificCause() != null ? dive.getMostSpecificCause().getMessage()
                                            : dive.getMessage());
                        }
                    } catch (Exception ex) {
                        failed.incrementAndGet();
                        this.log.warn("Skip message due to processing error: {}", ex.getMessage());
                    }
                }));
            }
            // wait all
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (CancellationException | InterruptedException | ExecutionException ex) {
                    executor.shutdownNow();
                    failed.incrementAndGet();
                    mailSyncLogService.complete(syncLog.getLogId(),
                            succeeded.get() + failed.get(), succeeded.get(), failed.get(), "failed", ex.getMessage());
                    throw new IllegalStateException("Failed to complete IMAP sync: " + ex.getMessage() , ex);
                }
            }
            executor.shutdown();
            folder.close(false);
            mailSyncLogService.complete(syncLog.getLogId(),
                    succeeded.get() + failed.get(), succeeded.get(), failed.get(), "completed", null);
            return succeeded.get();
        } catch (MessagingException ex) {
            mailSyncLogService.complete(syncLog.getLogId(), 0, 0, 1, "failed", ex.getMessage());

            if ( ex instanceof AuthenticationFailedException )
                throw UnAuthorizedException.of(ErrorType.of("error.mail.imap.authfailed", HttpStatus.UNAUTHORIZED), ex);
            
            throw new IllegalStateException("Failed to sync IMAP messages: " + ex.getMessage(), ex);
        } finally {
            running.set(false);
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

    private String joinAddresses(javax.mail.Address[] addresses) {
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
        if (content instanceof javax.mail.Multipart multipart) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                var bodyPart = multipart.getBodyPart(i);
                String disposition = bodyPart.getDisposition();
                boolean isAttachment = disposition != null
                        && disposition.equalsIgnoreCase(javax.mail.Part.ATTACHMENT);
                try {
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
                } catch (Exception ex) {
                    log.warn("Skip part due to parse error: {}", ex.getMessage());
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
