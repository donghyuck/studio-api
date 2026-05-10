package studio.one.platform.objecttype.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;

import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.objecttype.lifecycle.ObjectRebindService;
import studio.one.platform.objecttype.application.usecase.ObjectTypeAdminService;
import studio.one.platform.objecttype.application.result.ObjectTypeEffectivePolicyView;
import studio.one.platform.objecttype.application.command.ObjectTypePolicyUpsertCommand;
import studio.one.platform.objecttype.application.command.ObjectTypeUpsertCommand;
import studio.one.platform.objecttype.application.result.ObjectTypeView;
import studio.one.platform.objecttype.application.result.ObjectTypePolicyView;
import studio.one.platform.objecttype.web.dto.response.ObjectTypeEffectivePolicyDto;
import studio.one.platform.objecttype.web.dto.request.ObjectTypePolicyUpsertRequest;
import studio.one.platform.objecttype.web.dto.request.ObjectTypeUpsertRequest;
import studio.one.platform.web.dto.ApiResponse;

class ObjectTypeMgmtControllerTest {

    @Test
    void createMapsUpsertCommandAndResponse() {
        ObjectTypeAdminService adminService = mock(ObjectTypeAdminService.class);
        ObjectRebindService rebindService = mock(ObjectRebindService.class);
        PrincipalResolver principalResolver = mock(PrincipalResolver.class);
        ObjectTypeMgmtController controller = new ObjectTypeMgmtController(
                adminService,
                rebindService,
                principalResolverProvider(principalResolver));
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-03-30T01:02:03Z");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-03-30T04:05:06Z");
        when(principalResolver.currentOrNull()).thenReturn(principal(99L, "operator"));

        when(adminService.upsert(eq(new ObjectTypeUpsertCommand(1001, "attachment", "Attachment", "media",
                "active", "desc", "operator", 99L, "operator", 99L)))).thenReturn(
                        new ObjectTypeView(1001, "attachment", "Attachment", "media", "active", "desc",
                                "system", 1L, createdAt, "system", 1L, updatedAt));

        ResponseEntity<ApiResponse<studio.one.platform.objecttype.web.dto.response.ObjectTypeDto>> response =
                controller.create(new ObjectTypeUpsertRequest(1001, "attachment", "Attachment", "media",
                        "active", "desc", "system", 1L, "system", 1L));

        assertEquals(1001, response.getBody().getData().getObjectType());
        assertEquals("attachment", response.getBody().getData().getCode());
        verify(adminService).upsert(eq(new ObjectTypeUpsertCommand(1001, "attachment", "Attachment", "media",
                "active", "desc", "operator", 99L, "operator", 99L)));
    }

    @Test
    void upsertPolicyMapsCommandAndResponse() {
        ObjectTypeAdminService adminService = mock(ObjectTypeAdminService.class);
        ObjectRebindService rebindService = mock(ObjectRebindService.class);
        PrincipalResolver principalResolver = mock(PrincipalResolver.class);
        ObjectTypeMgmtController controller = new ObjectTypeMgmtController(
                adminService,
                rebindService,
                principalResolverProvider(principalResolver));
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-03-30T01:02:03Z");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-03-30T04:05:06Z");
        when(principalResolver.currentOrNull()).thenReturn(principal(99L, "operator"));

        when(adminService.upsertPolicy(eq(1001), eq(new ObjectTypePolicyUpsertCommand(12, "png", "image/png",
                "{\"x\":1}", "operator", 99L, "operator", 99L))))
                .thenReturn(new ObjectTypePolicyView(1001, 12, "png", "image/png", "{\"x\":1}",
                        "system", 1L, createdAt, "system", 1L, updatedAt));

        ResponseEntity<ApiResponse<studio.one.platform.objecttype.web.dto.response.ObjectTypePolicyDto>> response =
                controller.upsertPolicy(1001, new ObjectTypePolicyUpsertRequest(12, "png", "image/png",
                        "{\"x\":1}", "system", 1L, "system", 1L));

        assertEquals(12, response.getBody().getData().getMaxFileMb());
        assertEquals("png", response.getBody().getData().getAllowedExt());
        verify(adminService).upsertPolicy(eq(1001), eq(new ObjectTypePolicyUpsertCommand(12, "png", "image/png",
                "{\"x\":1}", "operator", 99L, "operator", 99L)));
    }

    @Test
    void effectivePolicyMapsStoredPolicy() {
        ObjectTypeAdminService adminService = mock(ObjectTypeAdminService.class);
        ObjectRebindService rebindService = mock(ObjectRebindService.class);
        ObjectTypeMgmtController controller = controller(adminService, rebindService);

        when(adminService.getEffectivePolicy(1001))
                .thenReturn(new ObjectTypeEffectivePolicyView(1001, 12, "png", "image/png", "{\"x\":1}",
                        ObjectTypeEffectivePolicyView.Source.STORED));

        ResponseEntity<ApiResponse<ObjectTypeEffectivePolicyDto>> response = controller.getEffectivePolicy(1001);

        assertEquals(1001, response.getBody().getData().getObjectType());
        assertEquals(12, response.getBody().getData().getMaxFileMb());
        assertEquals("png", response.getBody().getData().getAllowedExt());
        assertEquals("image/png", response.getBody().getData().getAllowedMime());
        assertEquals("{\"x\":1}", response.getBody().getData().getPolicyJson());
        assertEquals("stored", response.getBody().getData().getSource());
        verify(adminService).getEffectivePolicy(1001);
    }

    @Test
    void effectivePolicyMapsDefaultPolicy() {
        ObjectTypeAdminService adminService = mock(ObjectTypeAdminService.class);
        ObjectRebindService rebindService = mock(ObjectRebindService.class);
        ObjectTypeMgmtController controller = controller(adminService, rebindService);

        when(adminService.getEffectivePolicy(1001))
                .thenReturn(new ObjectTypeEffectivePolicyView(1001, null, null, null, null,
                        ObjectTypeEffectivePolicyView.Source.DEFAULT));

        ResponseEntity<ApiResponse<ObjectTypeEffectivePolicyDto>> response = controller.getEffectivePolicy(1001);

        assertEquals(1001, response.getBody().getData().getObjectType());
        assertNull(response.getBody().getData().getMaxFileMb());
        assertNull(response.getBody().getData().getAllowedExt());
        assertNull(response.getBody().getData().getAllowedMime());
        assertNull(response.getBody().getData().getPolicyJson());
        assertEquals("default", response.getBody().getData().getSource());
        verify(adminService).getEffectivePolicy(1001);
    }

    @Test
    void listMapsServiceViewsToWebDtos() {
        ObjectTypeAdminService adminService = mock(ObjectTypeAdminService.class);
        ObjectRebindService rebindService = mock(ObjectRebindService.class);
        ObjectTypeMgmtController controller = controller(adminService, rebindService);
        OffsetDateTime now = OffsetDateTime.parse("2026-03-30T01:02:03Z");

        when(adminService.search(null, null, null)).thenReturn(List.of(
                new ObjectTypeView(1001, "attachment", "Attachment", "media", "active", null,
                        "system", 1L, now, "system", 1L, now)));

        ResponseEntity<ApiResponse<List<studio.one.platform.objecttype.web.dto.response.ObjectTypeDto>>> response =
                controller.list(null, null, null);

        assertEquals(1, response.getBody().getData().size());
        assertEquals("attachment", response.getBody().getData().get(0).getCode());
        verify(adminService).search(null, null, null);
    }

    private ObjectTypeMgmtController controller(ObjectTypeAdminService adminService, ObjectRebindService rebindService) {
        return new ObjectTypeMgmtController(adminService, rebindService, principalResolverProvider(null));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<PrincipalResolver> principalResolverProvider(PrincipalResolver resolver) {
        ObjectProvider<PrincipalResolver> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(resolver);
        return provider;
    }

    private ApplicationPrincipal principal(Long userId, String username) {
        return new ApplicationPrincipal() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public String getUsername() {
                return username;
            }

            @Override
            public Set<String> getRoles() {
                return Set.of("ADMIN");
            }
        };
    }
}
