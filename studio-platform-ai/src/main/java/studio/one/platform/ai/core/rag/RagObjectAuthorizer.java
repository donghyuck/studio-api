package studio.one.platform.ai.core.rag;

/**
 * Host-provided authorization for RAG object scopes that require domain lookup.
 */
public interface RagObjectAuthorizer {

    boolean supports(String objectType);

    boolean canRead(String objectType, String objectId);
}
