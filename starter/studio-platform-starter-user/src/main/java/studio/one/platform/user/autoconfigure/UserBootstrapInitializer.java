package studio.one.platform.user.autoconfigure;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.domain.model.ApplicationRole;
import studio.one.base.user.domain.model.ApplicationUser;
import studio.one.base.user.domain.port.ApplicationRoleRepository;
import studio.one.base.user.domain.port.ApplicationUserRepository;

@RequiredArgsConstructor
@Slf4j
public class UserBootstrapInitializer implements ApplicationRunner {

    private static final String ACTOR = "user-bootstrap";

    private final UserBootstrapProperties properties;
    private final ApplicationRoleRepository roleRepository;
    private final ApplicationUserRepository userRepository;
    private final ObjectProvider<PasswordEncoder> passwordEncoderProvider;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting user bootstrap initialization... {}", properties.isEnabled() ? "enabled" : "disabled");
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
        if (roleRepository.findByName(roleName).isPresent()) {
            log.info("Bootstrap role already exists: {}", roleName);
            return;
        }
        ApplicationRole role = new ApplicationRole();
        role.setName(roleName);
        role.setDescription(StringUtils.hasText(definition.getDescription())
                ? definition.getDescription().trim()
                : roleName);
        log.info("Creating bootstrap role: {}", roleName);
        Long roleId = insertRole(role);
        if (roleId == null) {
            throw new IllegalStateException("Bootstrap role was not persisted: " + roleName);
        }
        log.info("Created bootstrap role: name={}, id={}", roleName, roleId);
    }

    private void ensureAdminUser(UserBootstrapProperties.Admin admin) {
        if (admin == null || !admin.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(admin.getPassword())) {
            log.warn("Bootstrap admin user is enabled but no password was provided.");
            return;
        }
        long userCount = userRepository.findAll(PageRequest.of(0, 1)).getTotalElements();
        if (userCount > 0) {
            log.info("Bootstrap admin user skipped because users already exist: count={}", userCount);
            return;
        }
        String username = StringUtils.hasText(admin.getUsername()) ? admin.getUsername().trim() : "local-admin";
        Optional<ApplicationUser> existing = userRepository.findByUsername(username);
        if (existing.isPresent()) {
            log.info("Bootstrap admin user already exists: {}", username);
            return;
        }

        PasswordEncoder passwordEncoder = Objects.requireNonNull(passwordEncoderProvider.getIfAvailable(),
                "PasswordEncoder is required for bootstrap admin user");
        ApplicationUser user = ApplicationUser.builder()
                .username(username)
                .email(StringUtils.hasText(admin.getEmail()) ? admin.getEmail().trim() : username + "@example.local")
                .name(StringUtils.hasText(admin.getName()) ? admin.getName().trim() : username)
                .password(passwordEncoder.encode(admin.getPassword()))
                .enabled(true)
                .external(false)
                .nameVisible(true)
                .emailVisible(true)
                .build();
        log.info("Creating bootstrap admin user: {}", username);
        Long userId = insertUser(user);
        if (userId == null) {
            throw new IllegalStateException("Bootstrap admin user was not persisted: " + username);
        }
        user.setUserId(userId);
        assignAdminRoles(user, admin.getRoles());
        log.info("Created bootstrap admin user: username={}, id={}", username, userId);
    }

    private void assignAdminRoles(ApplicationUser user, List<String> roleNames) {
        if (roleNames == null || user == null || user.getUserId() == null) {
            return;
        }
        roleNames.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(roleRepository::findByName)
                .flatMap(Optional::stream)
                .forEach(role -> {
                    insertUserRole(user.getUserId(), role.getRoleId());
                    log.info("Assigned bootstrap role to user: userId={}, role={}", user.getUserId(), role.getName());
                });
    }

    private Long insertRole(ApplicationRole role) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    insert into tb_application_role (name, description, creation_date, modified_date)
                    values (?, ?, current_timestamp, current_timestamp)
                    """, new String[] { "role_id" });
            ps.setString(1, role.getName());
            ps.setString(2, role.getDescription());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    private Long insertUser(ApplicationUser user) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    insert into tb_application_user
                        (username, name, email, password_hash, name_visible, email_visible,
                         user_enabled, user_external, failed_attempts, creation_date, modified_date)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                    """, new String[] { "user_id" });
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getName());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPassword());
            ps.setBoolean(5, user.isNameVisible());
            ps.setBoolean(6, user.isEmailVisible());
            ps.setBoolean(7, user.isEnabled());
            ps.setBoolean(8, user.isExternal());
            ps.setInt(9, user.getFailedAttempts());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    private void insertUserRole(Long userId, Long roleId) {
        jdbcTemplate.update("""
                insert into tb_application_user_roles (user_id, role_id, assigned_at, assigned_by)
                values (?, ?, current_timestamp, ?)
                """, userId, roleId, ACTOR);
    }
}
