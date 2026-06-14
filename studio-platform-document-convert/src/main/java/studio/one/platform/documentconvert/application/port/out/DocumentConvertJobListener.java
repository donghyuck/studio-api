package studio.one.platform.documentconvert.application.port.out;

import studio.one.platform.documentconvert.domain.model.DocumentConvertJob;

/**
 * Receives terminal document conversion state changes without coupling the
 * conversion module to downstream pipelines.
 */
public interface DocumentConvertJobListener {

    default void onCompleted(DocumentConvertJob job) {
    }

    default void onFailed(DocumentConvertJob job) {
    }

    default void onCanceled(DocumentConvertJob job) {
    }
}
