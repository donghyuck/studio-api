package studio.one.aplication.security.util;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.userdetails.ApplicationUserDetails;
import studio.one.base.user.domain.model.User;

@Slf4j
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class SecurityHelper {

    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public static Optional<User> getUser() {
        Authentication authentication = getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ApplicationUserDetails) {
            ApplicationUserDetails<? extends User> userDetails = (ApplicationUserDetails<? extends User>) authentication.getPrincipal();
            return Optional.of(userDetails.getDomainUser());
        }
        return Optional.empty();
    }   
}
