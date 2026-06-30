package studio.one.platform.documentconvert.application.port.out;

import java.io.InputStream;

import studio.one.platform.documentconvert.domain.model.DocumentConvertJob;

public interface DocumentConvertDirectResultStore {

    boolean supports(DocumentConvertJob job);

    String storeResult(DocumentConvertJob job, InputStream input);
}
