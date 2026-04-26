package studio.one.platform.ai.service.pipeline;

import studio.one.platform.ai.core.rag.RagIndexJobLogCode;
import studio.one.platform.ai.core.rag.RagIndexJobStep;

public interface RagIndexProgressListener {

    RagIndexProgressListener NOOP = new RagIndexProgressListener() {
    };

    static RagIndexProgressListener noop() {
        return NOOP;
    }

    default void onStarted() {
    }

    default void onStep(RagIndexJobStep step) {
    }

    default void onChunkCount(int chunkCount) {
    }

    default void onEmbeddedCount(int embeddedCount) {
    }

    default void onIndexedCount(int indexedCount) {
    }

    default void onInfo(RagIndexJobStep step, String message, String detail) {
    }

    default void onWarning(RagIndexJobStep step, RagIndexJobLogCode code, String message, String detail) {
    }

    default void onError(RagIndexJobStep step, RagIndexJobLogCode code, String message, String detail) {
    }

    default void onCompleted() {
    }
}
