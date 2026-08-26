package studio.one.platform.ai.core.rag.usability;

import java.util.Optional;

/**
 * Supplies source-specific, revision-scoped evidence without exposing raw metadata
 * interpretation to clients.
 */
public interface RagObjectUsabilityEvidenceContributor {

    boolean supports(String objectType, String objectId);

    Optional<DocumentUsabilityEvidence> contribute(String objectType, String objectId);
}
