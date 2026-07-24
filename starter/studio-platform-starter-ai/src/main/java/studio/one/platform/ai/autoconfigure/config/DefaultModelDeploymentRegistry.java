package studio.one.platform.ai.autoconfigure.config;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.model.ModelDeployment;
import studio.one.platform.ai.model.ModelDeploymentRegistry;
import studio.one.platform.ai.model.ModelWorkload;

public final class DefaultModelDeploymentRegistry implements ModelDeploymentRegistry {

    private final Map<String, ModelDeployment> deployments;
    private final Map<String, ChatPort> chatPorts;
    private final Map<String, EmbeddingPort> embeddingPorts;
    private final String defaultChatDeployment;
    private final String defaultEmbeddingDeployment;

    DefaultModelDeploymentRegistry(
            Map<String, ModelDeployment> deployments,
            Map<String, ChatPort> chatPorts,
            Map<String, EmbeddingPort> embeddingPorts,
            String defaultChatDeployment,
            String defaultEmbeddingDeployment) {
        this.deployments = Map.copyOf(deployments);
        this.chatPorts = Map.copyOf(chatPorts);
        this.embeddingPorts = Map.copyOf(embeddingPorts);
        this.defaultChatDeployment = normalize(defaultChatDeployment);
        this.defaultEmbeddingDeployment = normalize(defaultEmbeddingDeployment);
    }

    @Override
    public Optional<ModelDeployment> find(String deploymentId) {
        return Optional.ofNullable(deployments.get(normalize(deploymentId)));
    }

    @Override
    public List<ModelDeployment> deployments(ModelWorkload workload) {
        return deployments.values().stream()
                .filter(deployment -> workload == null || deployment.workload() == workload)
                .sorted(java.util.Comparator.comparing(ModelDeployment::deploymentId))
                .toList();
    }

    @Override
    public Optional<ModelDeployment> defaultDeployment(ModelWorkload workload) {
        return find(workload == ModelWorkload.EMBEDDING ? defaultEmbeddingDeployment : defaultChatDeployment);
    }

    @Override
    public ChatPort chatPort(String deploymentId) {
        return required(chatPorts, deploymentId, "chat");
    }

    @Override
    public EmbeddingPort embeddingPort(String deploymentId) {
        return required(embeddingPorts, deploymentId, "embedding");
    }

    private <T> T required(Map<String, T> ports, String requestedId, String workload) {
        String id = normalize(requestedId);
        if (id == null) {
            id = "embedding".equals(workload) ? defaultEmbeddingDeployment : defaultChatDeployment;
        }
        T port = ports.get(id);
        if (port == null && id != null) {
            ModelWorkload target = "embedding".equals(workload) ? ModelWorkload.EMBEDDING : ModelWorkload.CHAT;
            String providerRef = id;
            java.util.List<String> aliases = deployments.values().stream()
                    .filter(deployment -> deployment.workload() == target)
                    .filter(deployment -> deployment.providerRef().equals(providerRef))
                    .map(ModelDeployment::deploymentId)
                    .toList();
            if (aliases.size() == 1) {
                id = aliases.get(0);
                port = ports.get(id);
            } else if (aliases.size() > 1) {
                throw new IllegalArgumentException("Ambiguous " + workload + " provider: " + providerRef
                        + ". Use deploymentId instead.");
            }
        }
        if (port == null) {
            throw new IllegalArgumentException("Unknown " + workload + " deployment: " + id);
        }
        return port;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
