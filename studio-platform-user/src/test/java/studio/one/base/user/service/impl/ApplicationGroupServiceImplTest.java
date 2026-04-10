package studio.one.base.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import studio.one.base.user.domain.entity.ApplicationGroup;
import studio.one.base.user.persistence.ApplicationGroupMembershipRepository;
import studio.one.base.user.persistence.ApplicationGroupRepository;
import studio.one.base.user.persistence.ApplicationGroupRoleRepository;
import studio.one.base.user.persistence.ApplicationRoleRepository;
import studio.one.platform.service.I18n;

@ExtendWith(MockitoExtension.class)
class ApplicationGroupServiceImplTest {

    @Mock
    private ApplicationGroupRepository groupRepo;

    @Mock
    private ApplicationRoleRepository roleRepo;

    @Mock
    private ApplicationGroupMembershipRepository membershipRepo;

    @Mock
    private ApplicationGroupRoleRepository groupRoleRepo;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectProvider<I18n> i18nProvider;

    // ---------- setProperty ----------

    @Test
    void setPropertyRejectsMalformedKey() {
        // P2: key must match [A-Za-z0-9_.-]{1,100}
        assertThrows(IllegalArgumentException.class,
                () -> service().setProperty(1L, "bad key!", "val"));
        assertThrows(IllegalArgumentException.class,
                () -> service().setProperty(1L, "", "val"));
        assertThrows(IllegalArgumentException.class,
                () -> service().setProperty(1L, null, "val"));
        assertThrows(IllegalArgumentException.class,
                () -> service().setProperty(1L, "a".repeat(101), "val"));

        verify(groupRepo, never()).findById(any());
    }

    @Test
    void setPropertyAcceptsValidKey() {
        ApplicationGroup group = ApplicationGroup.builder().groupId(10L).name("g").build();
        when(groupRepo.findById(10L)).thenReturn(Optional.of(group));
        when(groupRepo.save(group)).thenReturn(group);

        service().setProperty(10L, "my.feature_flag", "true");

        assertEquals("true", group.getProperties().get("my.feature_flag"));
    }

    // ---------- replaceProperties ----------

    @Test
    void replacePropertiesRejectsMalformedKeyInBulk() {
        // P2: bulk replace must also validate every key
        Map<String, String> props = new HashMap<>();
        props.put("valid-key", "ok");
        props.put("invalid key!", "bad");

        assertThrows(IllegalArgumentException.class,
                () -> service().replaceProperties(1L, props));

        verify(groupRepo, never()).findById(any());
        verify(groupRepo, never()).save(any());
    }

    @Test
    void replacePropertiesAcceptsValidKeys() {
        ApplicationGroup group = ApplicationGroup.builder().groupId(10L).name("g").build();
        when(groupRepo.findById(10L)).thenReturn(Optional.of(group));
        when(groupRepo.save(group)).thenReturn(group);

        Map<String, String> props = Map.of("theme", "dark", "locale", "ko");
        service().replaceProperties(10L, props);

        verify(groupRepo).save(group);
        assertEquals("dark", group.getProperties().get("theme"));
        assertEquals("ko", group.getProperties().get("locale"));
    }

    @Test
    void replacePropertiesWithNullClearsProperties() {
        ApplicationGroup group = ApplicationGroup.builder().groupId(10L).name("g").build();
        when(groupRepo.findById(10L)).thenReturn(Optional.of(group));
        when(groupRepo.save(group)).thenReturn(group);

        service().replaceProperties(10L, null);

        verify(groupRepo).save(group);
        assertEquals(0, group.getProperties().size());
    }

    private ApplicationGroupServiceImpl service() {
        return new ApplicationGroupServiceImpl(
                groupRepo,
                roleRepo,
                membershipRepo,
                groupRoleRepo,
                jdbcTemplate,
                i18nProvider);
    }
}
