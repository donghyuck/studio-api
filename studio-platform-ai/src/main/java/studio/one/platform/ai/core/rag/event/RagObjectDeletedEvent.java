package studio.one.platform.ai.core.rag.event;

/**
 * Event published when a RAG object index history is deleted.
 *
 * @param objectType the type of the RAG object
 * @param objectId the ID of the RAG object
 */
public record RagObjectDeletedEvent(String objectType, String objectId) {
}
