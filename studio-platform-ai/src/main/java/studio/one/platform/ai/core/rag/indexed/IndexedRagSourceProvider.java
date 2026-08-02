package studio.one.platform.ai.core.rag.indexed;

import java.util.Optional;

/**
 * Resolves a revision-pinned, pre-indexed evidence source without exposing its
 * persistence implementation to the AI web module.
 */
public interface IndexedRagSourceProvider {

    boolean supports(String sourceType);

    Optional<ResolvedIndexedRagSource> resolve(String sourceId, String revisionId);

    default int maxSelectedSources() {
        return 10;
    }

    default IndexedRagSourceCapabilities capabilities() {
        return IndexedRagSourceCapabilities.singlePage(maxSelectedSources());
    }
}
