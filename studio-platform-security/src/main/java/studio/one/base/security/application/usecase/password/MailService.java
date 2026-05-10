package studio.one.base.security.application.usecase.password;

import studio.one.platform.constant.ServiceNames;

public interface MailService {
    
    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":security:password-reset-mailer";

    void sendPasswordResetMail(String to, String token);
}
