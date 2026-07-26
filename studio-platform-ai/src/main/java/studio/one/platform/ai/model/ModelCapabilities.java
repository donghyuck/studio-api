package studio.one.platform.ai.model;

/**
 * Canonical model capability identifiers exposed by the model catalog.
 */
public final class ModelCapabilities {

    public static final String PROMPT_CACHE_IMPLICIT = "prompt-cache-implicit";
    public static final String PROMPT_CACHE_ROUTING_KEY = "prompt-cache-routing-key";
    public static final String PROMPT_CACHE_EXPLICIT = "prompt-cache-explicit";

    private ModelCapabilities() {
    }
}
