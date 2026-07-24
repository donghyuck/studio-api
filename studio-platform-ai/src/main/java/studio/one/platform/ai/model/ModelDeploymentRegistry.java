package studio.one.platform.ai.model;

import java.util.List;
import java.util.Optional;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

public interface ModelDeploymentRegistry {

    Optional<ModelDeployment> find(String deploymentId);

    List<ModelDeployment> deployments(ModelWorkload workload);

    Optional<ModelDeployment> defaultDeployment(ModelWorkload workload);

    ChatPort chatPort(String deploymentId);

    EmbeddingPort embeddingPort(String deploymentId);
}
