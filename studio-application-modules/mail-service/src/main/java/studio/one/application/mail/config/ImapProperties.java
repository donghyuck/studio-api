package studio.one.application.mail.config;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImapProperties {
    @NotBlank
    private String host;

    @Min(1)
    @Max(65535)
    private int port = 993;

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    private String protocol = "imaps";

    private boolean ssl = true;

    private String folder = "INBOX";

    private int maxMessages = 500;

    /**
     * 동시 처리 스레드 수 (1 이상). 메시지/첨부 파싱과 저장을 병렬 처리할 때 사용.
     */
    @Min(1)
    private int concurrency = 1;

    /**
     * 개별 첨부 허용 최대 크기(바이트). 초과 시 첨부를 저장하지 않고 건너뛴다.
     */
    @Min(1)
    private long maxAttachmentBytes = 10 * 1024 * 1024; // 10MB

    /**
     * 본문 저장 최대 크기(바이트). 초과 시 잘라서 저장한다.
     */
    @Min(1)
    private long maxBodyBytes = 1 * 1024 * 1024; // 1MB
}
