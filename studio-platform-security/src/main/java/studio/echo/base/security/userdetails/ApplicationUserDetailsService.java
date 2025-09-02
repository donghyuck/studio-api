package studio.echo.base.security.userdetails;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.user.domain.model.Role;
import studio.echo.base.user.domain.model.User;
import studio.echo.base.user.exception.UserNotFoundException;
import studio.echo.base.user.service.ApplicationUserService;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.constant.ServiceNames;

@Service(ServiceNames.USER_DETAILS_SERVICE)
@RequiredArgsConstructor
@Slf4j
public class ApplicationUserDetailsService<T extends User> implements UserDetailsService {
 
    private final ApplicationUserService<T> userService;

    @Value("${"+ PropertyKeys.Security.PREFIX +".efault-roles:#{null}}")  // 프로퍼티 없으면 ROLE_USER 기본값
    private String[] defaultRoles;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        T user = userService.findByUsername(username)
            .orElseThrow(() -> UserNotFoundException.of(username)); 
        
        log.debug("found user {} : {}", user.getUsername(), user.getPassword());

        List<GrantedAuthority> authorities = getFinalUserAuthority(user);
        return new ApplicationUserDetails<>(user, authorities);
    }

    protected List<GrantedAuthority> getFinalUserAuthority(T user) {
        Set<Role> effectiveRoles = Optional
            .ofNullable(userService.findEffectiveRoles(user.getUserId()))
            .orElseGet(Set::of);
 
       LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Role r : effectiveRoles) {
            if (r != null) names.add(norm(r.getName()));
        }

        return names.stream().filter(Objects::nonNull)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toUnmodifiableList());
    }

    private String norm(String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim();
        return trimmed.startsWith("ROLE_") ? trimmed : "ROLE_" + trimmed;
    }
}
