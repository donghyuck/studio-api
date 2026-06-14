package studio.one.platform.documentconvert.application.port.out;

import java.io.InputStream;
import java.net.URI;

import studio.one.platform.documentconvert.domain.model.DocumentConvertJob;

public interface DocumentConvertStoragePort {
    TransferUrls prepareTransfer(DocumentConvertJob job);
    default String storeResult(DocumentConvertJob job, InputStream input) {
        throw new UnsupportedOperationException("Result upload is not supported");
    }
    String resultFileId(DocumentConvertJob job);
    URI resultDownloadUrl(DocumentConvertJob job);

    record TransferUrls(URI sourceUrl, URI uploadUrl, String uploadToken, String resultFileId) {
    }
}
