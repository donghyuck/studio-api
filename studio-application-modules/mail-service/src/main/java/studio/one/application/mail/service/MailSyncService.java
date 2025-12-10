package studio.one.application.mail.service;
import studio.one.platform.constant.ServiceNames;

public interface MailSyncService {

    public static final String SERVICE_NAME = ServiceNames.Featrues.PREFIX  + ":mail:sync-service";

    /**
     * IMAP에서 메일을 동기화한다(동기).
     *
     * @return 성공 처리 건수
     */
    int sync();

    /**
     * 미리 생성된 로그 컨텍스트로 동기화를 수행한다(비동기 오케스트레이션용).
     *
     * @param log 동기화 로그
     * @return 성공 처리 건수
     */
    int sync(studio.one.application.mail.domain.model.MailSyncLog log);
}
