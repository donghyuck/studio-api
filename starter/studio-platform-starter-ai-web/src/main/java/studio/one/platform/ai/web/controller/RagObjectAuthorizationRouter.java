package studio.one.platform.ai.web.controller;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.context.ApplicationContext;

import studio.one.platform.ai.core.rag.RagObjectAuthorizer;
import studio.one.platform.ai.core.rag.team.TeamRagScopeResolver;
import studio.one.platform.ai.web.dto.ChatRagRequestDto;
import studio.one.platform.ai.web.dto.IndexRequest;

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
        boolean hasIndexedSources = request.indexedWebSources() != null && !request.indexedWebSources().isEmpty();
        if (request.teamId() != null || request.workspaceId() != null) {
            if (request.teamId() == null
                    || normalize(request.objectType()) != null
                    || normalize(request.objectId()) != null
                    || hasIndexedSources) {
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
        String objectType = normalize(request.objectType());
        String objectId = normalize(request.objectId());
        if ((objectType == null) != (objectId == null)) {
            return false;
        }
        boolean hasObjectScope = objectType != null;
        if (hasObjectScope && !canRead(objectType, objectId)) {
            return false;
        }
        if (hasIndexedSources && !canReadIndexedSources(request)) {
            return false;
        }
        return hasObjectScope || hasIndexedSources;
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
        List<RagObjectAuthorizer> supporting = new java.util.ArrayList<>();
        for (RagObjectAuthorizer authorizer : authorizers) {
            try {
                if (authorizer.supports(objectType)) {
                    supporting.add(authorizer);
                }
            } catch (RuntimeException ex) {
                return false;
            }
        }
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

    public boolean canWrite(IndexRequest request) {
        if (request == null || request.metadata() == null) {
            return false;
        }
        String objectType = normalize(text(request.metadata().get("objectType")));
        String objectId = normalize(text(request.metadata().get("objectId")));
        if (objectType == null || objectId == null) {
            return false;
        }
        return canWrite(objectType, objectId);
    }

    public boolean canWrite(String requestedObjectType, Object requestedObjectId) {
        String objectType = normalize(requestedObjectType);
        String objectId = normalizeObjectId(requestedObjectId);
        if (objectType == null || objectId == null) {
            return false;
        }
        List<RagObjectAuthorizer> supporting = new java.util.ArrayList<>();
        for (RagObjectAuthorizer authorizer : authorizers) {
            try {
                if (authorizer.supports(objectType)) {
                    supporting.add(authorizer);
                }
            } catch (RuntimeException ex) {
                return false;
            }
        }
        if (!supporting.isEmpty()) {
            return supporting.stream().anyMatch(authorizer -> safeCanWrite(authorizer, objectType, objectId));
        }
        if ("attachment".equalsIgnoreCase(objectType)) {
            return false;
        }
        return canStrict("objects:" + objectType + ":" + objectId, "write")
                || canStrict("objects:" + objectType, "write");
    }

    public boolean canReadRagService() {
        return can("services:ai_rag", "read");
    }

    public boolean canWriteRagService() {
        return canStrict("services:ai_rag", "write");
    }

    public boolean canStrictEndpoint(String resource, String action) {
        return canStrict(resource, action);
    }

    private boolean safeCanRead(RagObjectAuthorizer authorizer, String objectType, String objectId) {
        try {
            return authorizer.canRead(objectType, objectId);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean safeCanWrite(RagObjectAuthorizer authorizer, String objectType, String objectId) {
        try {
            return authorizer.canWrite(objectType, objectId);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean canReadIndexedSources(ChatRagRequestDto request) {
        return request.indexedWebSources().stream()
                .allMatch(source -> source != null
                        && normalize(source.sourceId()) != null
                        && canRead("web_source", source.sourceId()));
    }

    private boolean can(String resource, String action) {
        return invokeEndpointAuthz("can", resource, action);
    }

    private boolean canStrict(String resource, String action) {
        return invokeEndpointAuthz("canStrict", resource, action);
    }

    private boolean invokeEndpointAuthz(String methodName, String resource, String action) {
        try {
            Object endpointAuthz = applicationContext.getBean("endpointAuthz");
            Method method = endpointAuthz.getClass().getMethod(methodName, String.class, String.class);
            return Boolean.TRUE.equals(method.invoke(endpointAuthz, resource, action));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeObjectId(Object value) {
        return value == null ? null : normalize(value.toString());
    }
}
