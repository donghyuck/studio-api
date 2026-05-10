package studio.one.base.user.application.service;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import studio.one.base.user.domain.model.ApplicationRole;
import studio.one.base.user.domain.model.ApplicationUser;
import studio.one.base.user.domain.port.ApplicationUserRepository;
import studio.one.base.user.domain.port.ApplicationUserRoleRepository;
import studio.one.platform.identity.IdentityConstants;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.UserRef;

@Service(IdentityConstants.SERVICE_NAME)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationIdentityService implements IdentityService {

    private final ApplicationUserRepository userRepository;
    private final ApplicationUserRoleRepository userRoleRepository;

    @Override
    public Optional<UserRef> findById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId).map(this::toUserRef);
    }

    @Override
    public Optional<UserRef> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username).map(this::toUserRef);
    }

    private UserRef toUserRef(ApplicationUser user) {
        Set<String> roles = userRoleRepository.findRolesByUserId(user.getUserId())
                .stream()
                .map(ApplicationRole::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new UserRef(user.getUserId(), user.getUsername(), roles);
    }
}
