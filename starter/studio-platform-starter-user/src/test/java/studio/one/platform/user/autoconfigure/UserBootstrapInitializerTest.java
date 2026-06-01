package studio.one.platform.user.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import studio.one.base.user.domain.model.ApplicationRole;
import studio.one.base.user.domain.model.ApplicationUser;
import studio.one.base.user.domain.port.ApplicationRoleRepository;
import studio.one.base.user.domain.port.ApplicationUserRepository;

class UserBootstrapInitializerTest {

    @Test
    void createsMissingRolesAndAdminWhenEnabledAndUserTableIsEmpty() throws Exception {
        UserBootstrapProperties properties = properties("local-password");
        Recorder recorder = new Recorder();

        new UserBootstrapInitializer(properties, recorder.roleRepository(), recorder.userRepository(), passwordEncoder(),
                recorder.jdbcTemplate()).run(null);

        assertThat(recorder.createdRoles).extracting(ApplicationRole::getName)
                .containsExactly("ROLE_ADMIN", "ROLE_USER");
        assertThat(recorder.createdUsers).extracting(ApplicationUser::getUsername)
                .containsExactly("local-admin");
        assertThat(recorder.assignedRoles).containsExactly("100:10:user-bootstrap");
    }

    @Test
    void skipsAdminWhenPasswordIsMissingButStillCreatesRoles() throws Exception {
        UserBootstrapProperties properties = properties(null);
        Recorder recorder = new Recorder();

        new UserBootstrapInitializer(properties, recorder.roleRepository(), recorder.userRepository(), passwordEncoder(),
                recorder.jdbcTemplate()).run(null);

        assertThat(recorder.createdRoles).extracting(ApplicationRole::getName)
                .containsExactly("ROLE_ADMIN", "ROLE_USER");
        assertThat(recorder.createdUsers).isEmpty();
        assertThat(recorder.assignedRoles).isEmpty();
    }

    @Test
    void skipsAdminWhenAnyUserAlreadyExists() throws Exception {
        UserBootstrapProperties properties = properties("local-password");
        Recorder recorder = new Recorder();
        recorder.roles.put("ROLE_ADMIN", role(10L, "ROLE_ADMIN"));
        recorder.roles.put("ROLE_USER", role(11L, "ROLE_USER"));
        recorder.existingUsers.add(ApplicationUser.builder().username("existing").build());

        new UserBootstrapInitializer(properties, recorder.roleRepository(), recorder.userRepository(), passwordEncoder(),
                recorder.jdbcTemplate()).run(null);

        assertThat(recorder.createdRoles).isEmpty();
        assertThat(recorder.createdUsers).isEmpty();
        assertThat(recorder.findByUsernameCalls).isEqualTo(0);
        assertThat(recorder.assignedRoles).isEmpty();
    }

    private static ObjectProvider<PasswordEncoder> passwordEncoder() {
        PasswordEncoder encoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "{test}" + rawPassword;
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return encodedPassword.equals(encode(rawPassword));
            }
        };
        return new ObjectProvider<>() {
            @Override
            public PasswordEncoder getObject(Object... args) {
                return encoder;
            }

            @Override
            public PasswordEncoder getIfAvailable() {
                return encoder;
            }

            @Override
            public PasswordEncoder getIfUnique() {
                return encoder;
            }

            @Override
            public PasswordEncoder getObject() {
                return encoder;
            }
        };
    }

    private static UserBootstrapProperties properties(String password) {
        UserBootstrapProperties properties = new UserBootstrapProperties();
        properties.setEnabled(true);
        properties.getAdmin().setEnabled(true);
        properties.getAdmin().setUsername("local-admin");
        properties.getAdmin().setEmail("local@example.local");
        properties.getAdmin().setPassword(password);
        return properties;
    }

    private static ApplicationRole role(Long id, String name) {
        ApplicationRole role = new ApplicationRole();
        role.setRoleId(id);
        role.setName(name);
        role.setDescription(name);
        return role;
    }

    private static class Recorder {
        private final Map<String, ApplicationRole> roles = new HashMap<>();
        private final List<ApplicationRole> createdRoles = new ArrayList<>();
        private final List<ApplicationUser> existingUsers = new ArrayList<>();
        private final List<ApplicationUser> createdUsers = new ArrayList<>();
        private final List<String> assignedRoles = new ArrayList<>();
        private int nextRoleId = 10;
        private int insertRoleIndex;
        private int findByUsernameCalls;

        ApplicationRoleRepository roleRepository() {
            return (ApplicationRoleRepository) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] { ApplicationRoleRepository.class },
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByName" -> Optional.ofNullable(roles.get((String) args[0]));
                        case "save" -> createRole((ApplicationRole) args[0]);
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        ApplicationUserRepository userRepository() {
            return (ApplicationUserRepository) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] { ApplicationUserRepository.class },
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findAll" -> new PageImpl<>(existingUsers);
                        case "findByUsername" -> {
                            findByUsernameCalls++;
                            yield Optional.empty();
                        }
                        case "save" -> createUser((ApplicationUser) args[0]);
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        JdbcTemplate jdbcTemplate() {
            return new JdbcTemplate() {
                @Override
                public int update(PreparedStatementCreator psc, KeyHolder generatedKeyHolder) {
                    if (createdRoles.size() < 2) {
                        String roleName = insertRoleIndex++ == 0 ? "ROLE_ADMIN" : "ROLE_USER";
                        ApplicationRole role = createRole(role(null, roleName));
                        generatedKeyHolder.getKeyList().add(Map.of("ROLE_ID", role.getRoleId()));
                    } else {
                        ApplicationUser user = ApplicationUser.builder().username("local-admin").build();
                        createUser(user);
                        generatedKeyHolder.getKeyList().add(Map.of("USER_ID", user.getUserId()));
                    }
                    return 1;
                }

                @Override
                public int update(String sql, Object... args) {
                    assignedRoles.add(args[0] + ":" + args[1] + ":user-bootstrap");
                    return 1;
                }
            };
        }

        private ApplicationRole createRole(ApplicationRole role) {
            role.setRoleId((long) nextRoleId++);
            roles.put(role.getName(), role);
            createdRoles.add(role);
            return role;
        }

        private ApplicationUser createUser(ApplicationUser user) {
            user.setUserId(100L);
            createdUsers.add(user);
            return user;
        }
    }
}
