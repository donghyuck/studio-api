package studio.one.platform.ai.web.dto;

public record TeamRagCapabilitiesDto(
        boolean enabled,
        int maxObjectScopes,
        boolean workspaceSubtreeSupported,
        String cacheIsolationVersion) {
}
