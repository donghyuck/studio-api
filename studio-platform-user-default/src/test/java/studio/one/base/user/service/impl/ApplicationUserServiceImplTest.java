package studio.one.base.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import studio.one.base.user.domain.entity.ApplicationUser;
import studio.one.base.user.persistence.ApplicationGroupMembershipRepository;
import studio.one.base.user.persistence.ApplicationGroupRepository;
import studio.one.base.user.persistence.ApplicationRoleRepository;
import studio.one.base.user.persistence.ApplicationUserRepository;
import studio.one.base.user.persistence.ApplicationUserRoleRepository;
import studio.one.base.user.service.PasswordPolicyService;
import studio.one.platform.service.DomainEvents;
import studio.one.platform.service.I18n;

@ExtendWith(MockitoExtension.class)
class ApplicationUserServiceImplTest {

    @Mock
    private ApplicationUserRepository userRepo;

    @Mock
    private ApplicationRoleRepository roleRepo;

    @Mock
    private ApplicationGroupRepository groupRepo;

    @Mock
    private ApplicationUserRoleRepository userRoleRepo;

    @Mock
    private ApplicationGroupMembershipRepository membershipRepo;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectProvider<PasswordEncoder> passwordEncoderProvider;

    @Mock
    private ObjectProvider<DomainEvents> domainEventsProvider;

    @Mock
    private ObjectProvider<I18n> i18nProvider;

    @Mock
    private ObjectProvider<PasswordPolicyService> passwordPolicyValidatorProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordPolicyService passwordPolicyService;

    @Test
    void updateDoesNotReencodeExistingPassword() {
        ApplicationUser user = ApplicationUser.builder()
                .userId(10L)
                .username("user")
                .password("{bcrypt}existing-hash")
                .build();
        when(userRepo.findById(10L)).thenReturn(Optional.of(user));
        when(userRepo.save(user)).thenReturn(user);

        ApplicationUser updated = service().update(10L, u -> u.setName("Updated"));

        assertEquals("{bcrypt}existing-hash", updated.getPassword());
        verify(passwordEncoderProvider, never()).getIfAvailable();
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void updateRejectsPasswordChanges() {
        ApplicationUser user = ApplicationUser.builder()
                .userId(10L)
                .username("user")
                .password("{bcrypt}existing-hash")
                .build();
        when(userRepo.findById(10L)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class,
                () -> service().update(10L, u -> u.setPassword("plain-password")));

        assertEquals("plain-password", user.getPassword());
        verify(userRepo, never()).save(any());
        verify(passwordEncoderProvider, never()).getIfAvailable();
    }

    @Test
    void resetPasswordEncodesNewPassword() {
        ApplicationUser user = ApplicationUser.builder()
                .userId(10L)
                .username("user")
                .password("{bcrypt}existing-hash")
                .build();
        when(userRepo.findById(10L)).thenReturn(Optional.of(user));
        when(passwordPolicyValidatorProvider.getIfAvailable()).thenReturn(passwordPolicyService);
        when(passwordEncoderProvider.getIfAvailable()).thenReturn(passwordEncoder);
        when(passwordEncoder.encode("NextPassword123!")).thenReturn("{bcrypt}next-hash");

        String reason = "test";

        service().resetPassword(10L, "NextPassword123!", "admin", reason);

        assertEquals("{bcrypt}next-hash", user.getPassword());
        verify(passwordPolicyService).validate("NextPassword123!");
        verify(userRepo).save(user);
    }

    // ---------- property tests ----------

    @Test
    void setPropertyRejectsReservedPrefix() {
        assertThrows(IllegalArgumentException.class,
                () -> service().setProperty(1L, "security.token", "val"));
        assertThrows(IllegalArgumentException.class,
                () -> service().setProperty(1L, "auth.secret", "val"));
        assertThrows(IllegalArgumentException.class,
                () -> service().setProperty(1L, "role.admin", "val"));
    }

    @Test
    void setPropertyRejectsMalformedKey() {
        assertThrows(IllegalArgumentException.class,
                () -> service().setProperty(1L, "bad key!", "val"));
        assertThrows(IllegalArgumentException.class,
                () -> service().setProperty(1L, "", "val"));
        assertThrows(IllegalArgumentException.class,
                () -> service().setProperty(1L, "a".repeat(101), "val"));
    }

    @Test
    void replacePropertiesRejectsReservedKeyInBulk() {
        // P1: bulk replace must enforce the same reserved-key guard as setProperty
        Map<String, String> props = new HashMap<>();
        props.put("theme", "dark");
        props.put("security.token", "leaked");

        assertThrows(IllegalArgumentException.class,
                () -> service().replaceProperties(1L, props));

        verify(userRepo, never()).findById(any());
        verify(userRepo, never()).save(any());
    }

    @Test
    void replacePropertiesAcceptsValidKeys() {
        ApplicationUser user = ApplicationUser.builder()
                .userId(10L)
                .username("user")
                .password("{bcrypt}hash")
                .build();
        when(userRepo.findById(10L)).thenReturn(Optional.of(user));
        when(userRepo.save(user)).thenReturn(user);

        Map<String, String> props = Map.of("theme", "dark", "locale", "ko");
        service().replaceProperties(10L, props);

        verify(userRepo).save(user);
        assertEquals("dark", user.getProperties().get("theme"));
        assertEquals("ko", user.getProperties().get("locale"));
    }

    private ApplicationUserServiceImpl service() {
        return new ApplicationUserServiceImpl(
                userRepo,
                roleRepo,
                groupRepo,
                userRoleRepo,
                membershipRepo,
                jdbcTemplate,
                passwordEncoderProvider,
                domainEventsProvider,
                Clock.systemUTC(),
                i18nProvider,
                new ApplicationUserMutator(),
                passwordPolicyValidatorProvider);
    }
}
