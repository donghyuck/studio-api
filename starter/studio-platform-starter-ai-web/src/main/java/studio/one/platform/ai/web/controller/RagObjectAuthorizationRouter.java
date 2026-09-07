package studio.one.platform.ai.web.controller;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.context.ApplicationContext;

import studio.one.platform.ai.core.rag.RagObjectAuthorizer;
import studio.one.platform.ai.core.rag.team.TeamRagScopeResolver;
import studio.one.platform.ai.web.dto.ChatRagRequestDto;

/**
 * Central object-scope authorization policy for generic RAG endpoints.
 */
public final class RagObjectAuthorizationRouter {

    private final ApplicationContext applicationContext;
    private final List<RagObjectAuthorizer> authorizers;
    private final Supplier<TeamRagScopeResolver> teamScopeResolver;

    public RagObjectAuthorizationRouter(ApplicationContext applicationContext) {
        this(applicationContext, List.of(), null);
    }

    public RagObjectAuthorizationRouter(
            ApplicationContext applicationContext,
            List<RagObjectAuthorizer> authorizers) {
        this(applicationContext, authorizers, null);
    }

    public RagObjectAuthorizationRouter(
            ApplicationContext applicationContext,
            List<RagObjectAuthorizer> authorizers,
            Supplier<TeamRagScopeResolver> teamScopeResolver) {
        this.applicationContext = applicationContext;
        this.authorizers = authorizers == null ? List.of() : List.copyOf(authorizers);
        this.teamScopeResolver = teamScopeResolver;
    }

    public boolean canRead(ChatRagRequestDto request) {
        if (request == null) {
            return false;
        }
        if (request.teamId() != null || request.workspaceId() != null) {
            if (request.teamId() == null
                    || normalize(request.objectType()) != null
                    || normalize(request.objectId()) != null) {
                return false;
            }
            TeamRagScopeResolver resolver = teamScopeResolver == null ? null : teamScopeResolver.get();
            try {
                return resolver != null
                        && resolver.resolveAuthorized(request.teamId(), request.workspaceId()).isPresent();
            } catch (RuntimeException ex) {
                return false;
            }
        }
        return canRead(request.objectType(), request.objectId());
    }

    public boolean canRead(String requestedObjectType, String requestedObjectId) {
        String objectType = normalize(requestedObjectType);
        String objectId = normalize(requestedObjectId);
        if (objectType == null && objectId == null) {
            return true;
        }
        if (objectType == null || objectId == null) {
            return false;
        }
        List<RagObjectAuthorizer> supporting = authorizers.stream()
                .filter(authorizer -> authorizer.supports(objectType))
                .toList();
        if (!supporting.isEmpty()) {
            return supporting.stream().anyMatch(authorizer -> safeCanRead(authorizer, objectType, objectId));
        }
        if ("attachment".equalsIgnoreCase(objectType)) {
            return false;
        }
        return can("objects:" + objectType + ":" + objectId, "read")
                || can("objects:" + objectType, "read");
    }

    public boolean canReadAll(String requestedObjectType, List<String> requestedObjectIds) {
        String objectType = normalize(requestedObjectType);
        if (objectType == null || requestedObjectIds == null || requestedObjectIds.isEmpty()) {
            return false;
        }
        return requestedObjectIds.stream()
                .map(RagObjectAuthorizationRouter::normalize)
                .allMatch(objectId -> objectId != null && canRead(objectType, objectId));
    }

    public boolean canReadRagService() {
        return can("services:ai_rag", "read");
    }

    private boolean safeCanRead(RagObjectAuthorizer authorizer, String objectType, String objectId) {
        try {
            return authorizer.canRead(objectType, objectId);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean can(String resource, String action) {
        try {
            Object endpointAuthz = applicationContext.getBean("endpointAuthz");
            Method method = endpointAuthz.getClass().getMethod("can", String.class, String.class);
            return Boolean.TRUE.equals(method.invoke(endpointAuthz, resource, action));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
