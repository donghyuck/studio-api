package studio.one.platform.ai.core.rag.event;

/**
 * Event published when a RAG object indexing job is successfully completed.
 *
 * @param objectType the type of the RAG object
 * @param objectId the ID of the RAG object
 */
public record RagObjectIndexedEvent(String objectType, String objectId) {
}
