package studio.one.platform.documentconvert.application.port.out;

import java.util.Optional;

import studio.one.platform.documentconvert.domain.model.DocumentConvertJob;

public interface DocumentConvertJobRepository {
    DocumentConvertJob save(DocumentConvertJob job);
    Optional<DocumentConvertJob> findByJobId(String jobId);
}
