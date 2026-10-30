package studio.one.platform.realtime.stomp.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import studio.one.platform.realtime.stomp.config.RealtimeStompProperties;

class EndpointRealtimeStompDestinationAuthorizerTest {

    @Test
    void allowsRegisteredProtectedDestinationsWhenEndpointPolicyAllowsRead() {
        EndpointRealtimeStompDestinationAuthorizer authorizer = authorizer(new AllowingEndpointAuthz(), List.of());
        Authentication principal = new TestingAuthenticationToken("user", "n/a", "ROLE_USER");

        assertThat(authorizer.isAllowed(principal, StompCommand.SUBSCRIBE, "/topic/mail-sync")).isTrue();
        assertThat(authorizer.isAllowed(principal, StompCommand.SUBSCRIBE, "/topic/skillgraph/extraction-jobs")).isTrue();
        assertThat(authorizer.isAllowed(principal, StompCommand.SUBSCRIBE, "/topic/skillgraph/extraction-jobs/job-1")).isTrue();
        assertThat(authorizer.isAllowed(principal, StompCommand.SUBSCRIBE, "/topic/skillgraph/jobs/job-1")).isTrue();
        assertThat(authorizer.isAllowed(principal, StompCommand.SUBSCRIBE, "/topic/ai/vectors/projections/proj-1")).isTrue();
    }

    @Test
    void deniesRegisteredProtectedDestinationsWhenEndpointPolicyDeniesRead() {
        EndpointRealtimeStompDestinationAuthorizer authorizer = authorizer(new DenyingEndpointAuthz(), List.of());
        Authentication principal = new TestingAuthenticationToken("user", "n/a", "ROLE_USER");

        assertThat(authorizer.isAllowed(principal, StompCommand.SUBSCRIBE, "/topic/mail-sync")).isFalse();
        assertThat(authorizer.isAllowed(principal, StompCommand.SUBSCRIBE, "/topic/skillgraph/extraction-jobs")).isFalse();
        assertThat(authorizer.isAllowed(principal, StompCommand.SUBSCRIBE, "/topic/skillgraph/jobs/job-1")).isFalse();
        assertThat(authorizer.isAllowed(principal, StompCommand.SUBSCRIBE, "/topic/ai/vectors/projections/proj-1")).isFalse();
    }

    @Test
    void appliesCustomMailDestinationWithSameMailPolicy() {
        RecordingEndpointAuthz endpointAuthz = new RecordingEndpointAuthz();
        EndpointRealtimeStompDestinationAuthorizer authorizer = authorizer(endpointAuthz, List.of("/custom-mail"));
        Authentication principal = new TestingAuthenticationToken("user", "n/a", "ROLE_USER");

        assertThat(authorizer.isAllowed(principal, StompCommand.SUBSCRIBE, "/topic/custom-mail")).isTrue();
        assertThat(endpointAuthz.resource).isEqualTo("features:mail");
        assertThat(endpointAuthz.action).isEqualTo("read");
    }

    @Test
    void deniesUnknownWildcardAmbiguousAndUserDestinations() {
        EndpointRealtimeStompDestinationAuthorizer authorizer = authorizer(new AllowingEndpointAuthz(), List.of());
        Authentication principal = new TestingAuthenticationToken("user", "n/a", "ROLE_USER");

        assertThat(authorizer.isAllowed(principal, StompCommand.SUBSCRIBE, "/topic/unknown")).isFalse();
        assertThat(authorizer.isAllowed(principal, StompCommand.SUBSCRIBE, "/topic/mail-*")).isFalse();
        assertThat(authorizer.isAllowed(principal, StompCommand.SUBSCRIBE, "/user/other/queue/mail-sync")).isFalse();
        assertThat(authorizer.isAllowed(principal, StompCommand.SUBSCRIBE, "/topic//mail-sync")).isFalse();
        assertThat(authorizer.isAllowed(principal, StompCommand.SUBSCRIBE, "/topic/mail-sync%2fextra")).isFalse();
    }

    @Test
    void deniesAllClientSendDestinations() {
        EndpointRealtimeStompDestinationAuthorizer authorizer = authorizer(new AllowingEndpointAuthz(), List.of());
        Authentication principal = new TestingAuthenticationToken("user", "n/a", "ROLE_USER");

        assertThat(authorizer.isAllowed(principal, StompCommand.SEND, "/topic/mail-sync")).isFalse();
        assertThat(authorizer.isAllowed(principal, StompCommand.SEND, "/user/user-2/queue/mail-sync")).isFalse();
        assertThat(authorizer.isAllowed(principal, StompCommand.SEND, "/app/demo")).isFalse();
    }

    @Test
    void deniesMissingEndpointAuthzAndNonAuthenticationPrincipal() {
        EndpointRealtimeStompDestinationAuthorizer authorizer = authorizer(null, List.of());

        assertThat(authorizer.isAllowed(() -> "plain", StompCommand.SUBSCRIBE, "/topic/mail-sync")).isFalse();
        assertThat(authorizer.isAllowed(
                new TestingAuthenticationToken("user", "n/a", "ROLE_USER"),
                StompCommand.SUBSCRIBE,
                "/topic/mail-sync")).isFalse();
    }

    @Test
    void restoresThreadLocalSecurityContextAfterPolicyCheck() {
        Authentication previous = new TestingAuthenticationToken("previous", "n/a", "ROLE_PREVIOUS");
        SecurityContextHolder.getContext().setAuthentication(previous);
        EndpointRealtimeStompDestinationAuthorizer authorizer = authorizer(new AllowingEndpointAuthz(), List.of());

        assertThat(authorizer.isAllowed(
                new TestingAuthenticationToken("user", "n/a", "ROLE_USER"),
                StompCommand.SUBSCRIBE,
                "/topic/mail-sync")).isTrue();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previous);
        SecurityContextHolder.clearContext();
    }

    private EndpointRealtimeStompDestinationAuthorizer authorizer(Object endpointAuthz, List<String> mailDestinations) {
        return new EndpointRealtimeStompDestinationAuthorizer(new RealtimeStompProperties(), endpointAuthz, mailDestinations);
    }

    public static class AllowingEndpointAuthz {
        public boolean can(String resource, String action) {
            return "read".equals(action);
        }
    }

    public static class DenyingEndpointAuthz {
        public boolean can(String resource, String action) {
            return false;
        }
    }

    public static class RecordingEndpointAuthz {
        private String resource;
        private String action;

        public boolean can(String resource, String action) {
            this.resource = resource;
            this.action = action;
            return true;
        }
    }
}
