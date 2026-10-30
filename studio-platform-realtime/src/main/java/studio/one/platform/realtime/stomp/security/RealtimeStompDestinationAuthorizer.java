package studio.one.platform.realtime.stomp.security;

import java.security.Principal;

import org.springframework.messaging.simp.stomp.StompCommand;

public interface RealtimeStompDestinationAuthorizer {

    boolean isAllowed(Principal principal, StompCommand command, String destination);
}
