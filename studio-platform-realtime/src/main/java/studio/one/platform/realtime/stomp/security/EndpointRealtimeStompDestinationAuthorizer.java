package studio.one.platform.realtime.stomp.security;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.messaging.simp.stomp.StompCommand;

import studio.one.platform.realtime.stomp.config.RealtimeStompProperties;

public class EndpointRealtimeStompDestinationAuthorizer implements RealtimeStompDestinationAuthorizer {

    private final RealtimeStompProperties properties;
    private final Object endpointAuthz;
    private final Map<String, Policy> topicPolicies;

    public EndpointRealtimeStompDestinationAuthorizer(
            RealtimeStompProperties properties,
            Object endpointAuthz,
            List<String> mailDestinations) {
        this.properties = properties;
        this.endpointAuthz = endpointAuthz;
        this.topicPolicies = topicPolicies(mailDestinations);
    }

    @Override
    public boolean isAllowed(Principal principal, StompCommand command, String destination) {
        if (principal == null || endpointAuthz == null || command == null) {
            return false;
        }
        if (StompCommand.SEND.equals(command)) {
            return false;
        }
        if (!StompCommand.SUBSCRIBE.equals(command)) {
            return true;
        }
        String topicDestination = topicDestination(destination);
        if (topicDestination == null) {
            return false;
        }
        Policy policy = topicPolicies.get(topicDestination);
        if (policy == null) {
            return false;
        }
        return can(principal, policy.resource(), policy.action());
    }

    private Map<String, Policy> topicPolicies(List<String> mailDestinations) {
        Map<String, Policy> policies = new LinkedHashMap<>();
        for (String destination : mailDestinations == null ? List.<String>of() : mailDestinations) {
            put(policies, destination, "features:mail", "read");
        }
        put(policies, "/mail-sync", "features:mail", "read");
        put(policies, "/skillgraph/extraction-jobs", "features:skillgraph", "read");
        put(policies, "/skillgraph/jobs/{id}", "features:skillgraph", "read");
        put(policies, "/ai/vectors/projections/{id}", "services:ai_vector", "read");
        return Map.copyOf(policies);
    }

    private void put(Map<String, Policy> policies, String destination, String resource, String action) {
        String normalized = normalizeDestination(destination);
        if (normalized != null) {
            policies.put(normalized, new Policy(resource, action));
        }
    }

    private String topicDestination(String destination) {
        String normalized = normalizeDestination(destination);
        if (normalized == null || containsWildcard(normalized)) {
            return null;
        }
        String topicPrefix = normalizeDestination(properties.getTopicPrefix());
        String userPrefix = normalizeDestination(properties.getUserPrefix());
        if (userPrefix != null && normalized.startsWith(userPrefix + "/")) {
            return null;
        }
        if (topicPrefix == null || !normalized.startsWith(topicPrefix + "/")) {
            return null;
        }
        String topicDestination = normalized.substring(topicPrefix.length());
        if (matchesOneSegment(topicDestination, "/skillgraph/extraction-jobs/")) {
            return "/skillgraph/extraction-jobs";
        }
        if (matchesOneSegment(topicDestination, "/skillgraph/jobs/")) {
            return "/skillgraph/jobs/{id}";
        }
        if (matchesOneSegment(topicDestination, "/ai/vectors/projections/")) {
            return "/ai/vectors/projections/{id}";
        }
        return topicDestination;
    }

    private boolean matchesOneSegment(String destination, String prefix) {
        if (!destination.startsWith(prefix)) {
            return false;
        }
        String id = destination.substring(prefix.length());
        return !id.isBlank() && !id.contains("/");
    }

    private String normalizeDestination(String destination) {
        if (destination == null) {
            return null;
        }
        String value = destination.trim();
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        if (value.isBlank()
                || value.contains("\\")
                || value.contains("//")
                || value.toLowerCase(Locale.ROOT).contains("%2f")) {
            return null;
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean containsWildcard(String destination) {
        return destination.contains("*") || destination.contains("{") || destination.contains("}");
    }

    private boolean can(Principal principal, String resource, String action) {
        if (!isAuthenticated(principal)) {
            return false;
        }
        Class<?> securityContextHolderType = null;
        Object previous = null;
        try {
            securityContextHolderType = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Class<?> securityContextType = Class.forName("org.springframework.security.core.context.SecurityContext");
            Class<?> authenticationType = Class.forName("org.springframework.security.core.Authentication");
            previous = securityContextHolderType.getMethod("getContext").invoke(null);
            Object context = securityContextHolderType.getMethod("createEmptyContext").invoke(null);
            context.getClass().getMethod("setAuthentication", authenticationType).invoke(context, principal);
            securityContextHolderType.getMethod("setContext", securityContextType).invoke(null, context);
            Method method = endpointAuthz.getClass().getMethod("can", String.class, String.class);
            method.setAccessible(true);
            Object result = method.invoke(endpointAuthz, resource, action);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        } finally {
            restoreSecurityContext(securityContextHolderType, previous);
        }
    }

    private boolean isAuthenticated(Principal principal) {
        try {
            Class<?> authenticationType = Class.forName("org.springframework.security.core.Authentication");
            if (!authenticationType.isInstance(principal)) {
                return false;
            }
            return Boolean.TRUE.equals(authenticationType.getMethod("isAuthenticated").invoke(principal));
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    private void restoreSecurityContext(Class<?> securityContextHolderType, Object previous) {
        if (securityContextHolderType == null || previous == null) {
            return;
        }
        try {
            Class<?> securityContextType = Class.forName("org.springframework.security.core.context.SecurityContext");
            securityContextHolderType.getMethod("setContext", securityContextType).invoke(null, previous);
        } catch (ReflectiveOperationException | LinkageError ex) {
            // Authorization stays fail-closed; restoration errors should not widen access.
        }
    }

    private record Policy(String resource, String action) {
    }
}
