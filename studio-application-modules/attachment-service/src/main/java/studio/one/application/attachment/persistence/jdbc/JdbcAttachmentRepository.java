package studio.one.application.attachment.persistence.jdbc;
import org.springframework.jdbc.core.JdbcTemplate;
import studio.one.application.attachment.persistence.AttachmentRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JdbcAttachmentRepository implements AttachmentRepository {
    
    private final JdbcTemplate jdbc;

}
