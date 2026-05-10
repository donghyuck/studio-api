package studio.one.application.attachment.web.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.util.StringUtils;

public class AttachmentUrlIssueRequestDetailsResolver {

    private final boolean trustForwardedFor;

    public AttachmentUrlIssueRequestDetailsResolver(boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    AttachmentUrlIssueRequestDetails resolve(HttpServletRequest request) {
        if (request == null) {
            return new AttachmentUrlIssueRequestDetails(null, null);
        }
        return new AttachmentUrlIssueRequestDetails(resolveClientIp(request), trimToNull(request.getHeader("User-Agent")));
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwardedFor)) {
                return trimToNull(forwardedFor.split(",", 2)[0]);
            }
        }
        return trimToNull(request.getRemoteAddr());
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
