package studio.one.platform.user.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import studio.one.base.user.application.usecase.ApplicationRoleService;
import studio.one.base.user.application.usecase.ApplicationUserService;
import studio.one.base.user.domain.model.ApplicationRole;
import studio.one.base.user.domain.model.ApplicationUser;

class UserBootstrapInitializerTest {

    @Test
    void createsMissingRolesAndAdminWhenEnabledAndUserTableIsEmpty() throws Exception {
        UserBootstrapProperties properties = properties("local-password");
        Recorder recorder = new Recorder();

        new UserBootstrapInitializer(properties, recorder.roleService(), recorder.userService()).run(null);

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

        new UserBootstrapInitializer(properties, recorder.roleService(), recorder.userService()).run(null);

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

        new UserBootstrapInitializer(properties, recorder.roleService(), recorder.userService()).run(null);

        assertThat(recorder.createdRoles).isEmpty();
        assertThat(recorder.createdUsers).isEmpty();
        assertThat(recorder.findByUsernameCalls).isEqualTo(0);
        assertThat(recorder.assignedRoles).isEmpty();
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
        private int findByUsernameCalls;

        @SuppressWarnings("unchecked")
        ApplicationRoleService<ApplicationRole, ?> roleService() {
            return (ApplicationRoleService<ApplicationRole, ?>) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] { ApplicationRoleService.class },
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findRoleByName" -> Optional.ofNullable(roles.get((String) args[0]));
                        case "createRole" -> createRole((ApplicationRole) args[0]);
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        @SuppressWarnings("unchecked")
        ApplicationUserService<ApplicationUser, ApplicationRole> userService() {
            return (ApplicationUserService<ApplicationUser, ApplicationRole>) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] { ApplicationUserService.class },
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findAll" -> new PageImpl<>(existingUsers);
                        case "findByUsername" -> {
                            findByUsernameCalls++;
                            yield Optional.empty();
                        }
                        case "create" -> createUser((ApplicationUser) args[0]);
                        case "assignRole" -> {
                            assignedRoles.add(args[0] + ":" + args[1] + ":" + args[2]);
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
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
