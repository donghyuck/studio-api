package studio.one.platform.ai.core.rag.external;

import java.util.List;

/**
 * Supplies source-verified evidence from an official external source.
 *
 * <p>Implementations must return excerpts copied from the fetched canonical
 * source. Search-result snippets must not be returned as verified evidence.</p>
 */
public interface ExternalEvidenceProvider {

    String providerId();

    boolean supports(ExternalEvidenceRequest request);

    List<ExternalEvidence> retrieve(ExternalEvidenceRequest request);
}
