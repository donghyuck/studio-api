package studio.one.application.attachment.web.controller;

class AttachmentUrlIssueRequestDetails {

    private final String clientIp;
    private final String userAgent;

    public AttachmentUrlIssueRequestDetails(
            String clientIp,
            String userAgent) {
        this.clientIp = clientIp;
        this.userAgent = userAgent;
    }

    public String clientIp() { return clientIp; }

    public String userAgent() { return userAgent; }

}
