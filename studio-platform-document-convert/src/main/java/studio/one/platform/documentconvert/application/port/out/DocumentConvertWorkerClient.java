package studio.one.platform.documentconvert.application.port.out;

import java.net.URI;
import java.util.Map;

import studio.one.platform.documentconvert.domain.model.DocumentConvertJob;

public interface DocumentConvertWorkerClient {
    void submit(DocumentConvertJob job, URI sourceUrl, URI uploadUrl, String uploadToken, URI callbackUrl,
            String resultFileId, Map<String, Object> options);
}
