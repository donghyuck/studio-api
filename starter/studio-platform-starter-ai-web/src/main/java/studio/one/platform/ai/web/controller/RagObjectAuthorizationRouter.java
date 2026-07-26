package studio.one.platform.ai.web.controller;

import java.lang.reflect.Method;

import org.springframework.context.ApplicationContext;

import studio.one.platform.ai.web.dto.ChatRagRequestDto;

/**
 * Central object-scope authorization policy for generic RAG endpoints.
 */
public final class RagObjectAuthorizationRouter {

    private final ApplicationContext applicationContext;

    public RagObjectAuthorizationRouter(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public boolean canRead(ChatRagRequestDto request) {
        if (request == null) {
            return false;
        }
        String objectType = normalize(request.objectType());
        String objectId = normalize(request.objectId());
        if (objectType == null && objectId == null) {
            return true;
        }
        if (objectType == null || objectId == null) {
            return true;
        }
        if ("attachment".equalsIgnoreCase(objectType)) {
            return can("features:attachment", "read");
        }
        return can("objects:" + objectType + ":" + objectId, "read")
                || can("objects:" + objectType, "read");
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
