package studio.one.platform.ai.core.rag;

/**
 * Host-provided authorization for RAG object scopes that require domain lookup.
 */
public interface RagObjectAuthorizer {

    boolean supports(String objectType);

    boolean canRead(String objectType, String objectId);

    /**
     * Authorizes mutation of an existing RAG object scope.
     *
     * <p>The default is fail-closed so legacy authorizers do not accidentally grant
     * indexing or delete permissions.</p>
     */
    default boolean canWrite(String objectType, String objectId) {
        return false;
    }
}
