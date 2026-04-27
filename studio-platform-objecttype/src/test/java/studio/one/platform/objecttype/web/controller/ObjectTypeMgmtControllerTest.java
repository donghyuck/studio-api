package studio.one.platform.objecttype.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import studio.one.platform.objecttype.lifecycle.ObjectRebindService;
import studio.one.platform.objecttype.service.ObjectTypeAdminService;
import studio.one.platform.objecttype.service.ObjectTypeEffectivePolicyView;
import studio.one.platform.objecttype.service.ObjectTypePolicyUpsertCommand;
import studio.one.platform.objecttype.service.ObjectTypeUpsertCommand;
import studio.one.platform.objecttype.service.ObjectTypeView;
import studio.one.platform.objecttype.service.ObjectTypePolicyView;
import studio.one.platform.objecttype.web.dto.ObjectTypeEffectivePolicyDto;
import studio.one.platform.objecttype.web.dto.ObjectTypePolicyUpsertRequest;
import studio.one.platform.objecttype.web.dto.ObjectTypeUpsertRequest;
import studio.one.platform.web.dto.ApiResponse;

class ObjectTypeMgmtControllerTest {

    @Test
    void createMapsUpsertCommandAndResponse() {
        ObjectTypeAdminService adminService = mock(ObjectTypeAdminService.class);
        ObjectRebindService rebindService = mock(ObjectRebindService.class);
        ObjectTypeMgmtController controller = new ObjectTypeMgmtController(adminService, rebindService);
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-03-30T01:02:03Z");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-03-30T04:05:06Z");

        when(adminService.upsert(eq(new ObjectTypeUpsertCommand(1001, "attachment", "Attachment", "media",
                "active", "desc", "system", 1L, "system", 1L)))).thenReturn(
                        new ObjectTypeView(1001, "attachment", "Attachment", "media", "active", "desc",
                                "system", 1L, createdAt, "system", 1L, updatedAt));

        ResponseEntity<ApiResponse<studio.one.platform.objecttype.web.dto.ObjectTypeDto>> response =
                controller.create(new ObjectTypeUpsertRequest(1001, "attachment", "Attachment", "media",
                        "active", "desc", "system", 1L, "system", 1L));

        assertEquals(1001, response.getBody().getData().getObjectType());
        assertEquals("attachment", response.getBody().getData().getCode());
        verify(adminService).upsert(eq(new ObjectTypeUpsertCommand(1001, "attachment", "Attachment", "media",
                "active", "desc", "system", 1L, "system", 1L)));
    }

    @Test
    void upsertPolicyMapsCommandAndResponse() {
        ObjectTypeAdminService adminService = mock(ObjectTypeAdminService.class);
        ObjectRebindService rebindService = mock(ObjectRebindService.class);
        ObjectTypeMgmtController controller = new ObjectTypeMgmtController(adminService, rebindService);
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-03-30T01:02:03Z");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-03-30T04:05:06Z");

        when(adminService.upsertPolicy(eq(1001), eq(new ObjectTypePolicyUpsertCommand(12, "png", "image/png",
                "{\"x\":1}", "system", 1L, "system", 1L))))
                .thenReturn(new ObjectTypePolicyView(1001, 12, "png", "image/png", "{\"x\":1}",
                        "system", 1L, createdAt, "system", 1L, updatedAt));

        ResponseEntity<ApiResponse<studio.one.platform.objecttype.web.dto.ObjectTypePolicyDto>> response =
                controller.upsertPolicy(1001, new ObjectTypePolicyUpsertRequest(12, "png", "image/png",
                        "{\"x\":1}", "system", 1L, "system", 1L));

        assertEquals(12, response.getBody().getData().getMaxFileMb());
        assertEquals("png", response.getBody().getData().getAllowedExt());
        verify(adminService).upsertPolicy(eq(1001), eq(new ObjectTypePolicyUpsertCommand(12, "png", "image/png",
                "{\"x\":1}", "system", 1L, "system", 1L)));
    }

    @Test
    void effectivePolicyMapsStoredPolicy() {
        ObjectTypeAdminService adminService = mock(ObjectTypeAdminService.class);
        ObjectRebindService rebindService = mock(ObjectRebindService.class);
        ObjectTypeMgmtController controller = new ObjectTypeMgmtController(adminService, rebindService);

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
        ObjectTypeMgmtController controller = new ObjectTypeMgmtController(adminService, rebindService);

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
        ObjectTypeMgmtController controller = new ObjectTypeMgmtController(adminService, rebindService);
        OffsetDateTime now = OffsetDateTime.parse("2026-03-30T01:02:03Z");

        when(adminService.search(null, null, null)).thenReturn(List.of(
                new ObjectTypeView(1001, "attachment", "Attachment", "media", "active", null,
                        "system", 1L, now, "system", 1L, now)));

        ResponseEntity<ApiResponse<List<studio.one.platform.objecttype.web.dto.ObjectTypeDto>>> response =
                controller.list(null, null, null);

        assertEquals(1, response.getBody().getData().size());
        assertEquals("attachment", response.getBody().getData().get(0).getCode());
        verify(adminService).search(null, null, null);
    }
}
