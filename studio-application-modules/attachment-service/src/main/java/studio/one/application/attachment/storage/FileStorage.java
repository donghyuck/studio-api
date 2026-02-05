package studio.one.application.attachment.storage;

import studio.one.application.attachment.domain.model.Attachment;
import java.io.InputStream;

public interface FileStorage {
    String save(
            Attachment attachment,
            InputStream input
    );

    /** 파일 읽기 */
    InputStream load(Attachment attachment);

    /** 파일 삭제 */
    void delete(Attachment attachment);

}