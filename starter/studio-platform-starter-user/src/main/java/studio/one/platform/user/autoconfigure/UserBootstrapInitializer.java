package studio.one.platform.user.autoconfigure;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.application.usecase.ApplicationRoleService;
import studio.one.base.user.application.usecase.ApplicationUserService;
import studio.one.base.user.domain.model.ApplicationRole;
import studio.one.base.user.domain.model.ApplicationUser;

@RequiredArgsConstructor
@Slf4j
public class UserBootstrapInitializer implements ApplicationRunner {

    private static final String ACTOR = "user-bootstrap";

    private final UserBootstrapProperties properties;
    private final ApplicationRoleService<ApplicationRole, ?> roleService;
    private final ApplicationUserService<ApplicationUser, ApplicationRole> userService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        ensureRoles(properties.getRoles());
        ensureAdminUser(properties.getAdmin());
    }

    private void ensureRoles(List<UserBootstrapProperties.RoleDefinition> roles) {
        if (roles == null) {
            return;
        }
        roles.stream()
                .filter(Objects::nonNull)
                .filter(role -> StringUtils.hasText(role.getName()))
                .forEach(this::ensureRole);
    }

    private void ensureRole(UserBootstrapProperties.RoleDefinition definition) {
        String roleName = definition.getName().trim();
        if (roleService.findRoleByName(roleName).isPresent()) {
            return;
        }
        ApplicationRole role = new ApplicationRole();
        role.setName(roleName);
        role.setDescription(StringUtils.hasText(definition.getDescription())
                ? definition.getDescription().trim()
                : roleName);
        roleService.createRole(role);
        log.info("Created bootstrap role: {}", roleName);
    }

    private void ensureAdminUser(UserBootstrapProperties.Admin admin) {
        if (admin == null || !admin.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(admin.getPassword())) {
            log.warn("Bootstrap admin user is enabled but no password was provided.");
            return;
        }
        if (userService.findAll(PageRequest.of(0, 1)).getTotalElements() > 0) {
            log.info("Bootstrap admin user skipped because users already exist.");
            return;
        }
        String username = StringUtils.hasText(admin.getUsername()) ? admin.getUsername().trim() : "local-admin";
        Optional<ApplicationUser> existing = userService.findByUsername(username);
        if (existing.isPresent()) {
            log.info("Bootstrap admin user already exists: {}", username);
            return;
        }

        ApplicationUser user = ApplicationUser.builder()
                .username(username)
                .email(StringUtils.hasText(admin.getEmail()) ? admin.getEmail().trim() : username + "@example.local")
                .name(StringUtils.hasText(admin.getName()) ? admin.getName().trim() : username)
                .password(admin.getPassword())
                .enabled(true)
                .external(false)
                .nameVisible(true)
                .emailVisible(true)
                .build();
        ApplicationUser saved = userService.create(user);
        assignAdminRoles(saved, admin.getRoles());
        log.info("Created bootstrap admin user: {}", username);
    }

    private void assignAdminRoles(ApplicationUser user, List<String> roleNames) {
        if (roleNames == null || user == null || user.getUserId() == null) {
            return;
        }
        roleNames.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(roleService::findRoleByName)
                .flatMap(Optional::stream)
                .forEach(role -> userService.assignRole(user.getUserId(), role.getRoleId(), ACTOR));
    }
}
