package studio.one.application.attachment.storage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.util.StringUtils;

import studio.one.application.attachment.domain.model.Attachment;

public final class AttachmentContentDisposition {

    private AttachmentContentDisposition() {
    }

    public static String attachment(Attachment attachment) {
        String original = attachment == null ? null : attachment.getName();
        String fallback = asciiFallback(original, attachment == null ? 0L : attachment.getAttachmentId());
        String encoded = encode(StringUtils.hasText(original) ? original : fallback);
        return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }

    static String asciiFallback(String original, long attachmentId) {
        String filename = StringUtils.hasText(original)
                ? StringUtils.getFilename(original.replace("\\", "/"))
                : null;
        if (!StringUtils.hasText(filename)) {
            filename = "attachment-" + attachmentId;
        }
        StringBuilder out = new StringBuilder(filename.length());
        for (int i = 0; i < filename.length(); i++) {
            char ch = filename.charAt(i);
            if (ch <= 0x1F || ch == 0x7F || ch == '"' || ch == '\\' || ch == '/' || ch > 0x7E) {
                out.append('_');
            } else {
                out.append(ch);
            }
        }
        String fallback = out.toString().trim();
        return fallback.isBlank() ? "attachment-" + attachmentId : fallback;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
