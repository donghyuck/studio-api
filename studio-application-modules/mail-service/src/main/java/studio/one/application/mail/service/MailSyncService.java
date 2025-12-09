package studio.one.application.mail.service;
import studio.one.platform.constant.ServiceNames;

public interface MailSyncService {

    public static final String SERVICE_NAME = ServiceNames.Featrues.PREFIX  + ":mail:sync-service";

    /**
     * Synchronize messages from IMAP into the database.
     *
     * @return number of messages processed
     */
    int sync();
}
