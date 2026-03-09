package studio.one.platform.storage.web.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import studio.one.platform.service.I18n;
import studio.one.platform.storage.service.ObjectStorageRegistry;
import studio.one.platform.storage.service.ProviderCatalog;

class ObjectStorageControllerAuthorizationTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void providersRejectNonAdmin() {
        ObjectStorageController controller = new ObjectStorageController(
                mock(ObjectStorageRegistry.class),
                mock(ProviderCatalog.class),
                mock(I18n.class));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThrows(AccessDeniedException.class, () -> controller.listProviders(false));
    }

    @Test
    void presignedPutRejectsAnonymous() {
        ObjectStorageController controller = new ObjectStorageController(
                mock(ObjectStorageRegistry.class),
                mock(ProviderCatalog.class),
                mock(I18n.class));

        assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
                () -> controller.presignedPut("p1", "bucket", new ObjectStorageController.PresignedPutRequest()));
    }
}
