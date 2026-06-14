package studio.one.platform.ai.service.pipeline;

import java.util.Map;

@FunctionalInterface
public interface RagObjectMetadataContributor {

    Map<String, Object> contribute(String objectType, String objectId);
}
