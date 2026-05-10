package studio.one.platform.objecttype.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import studio.one.platform.objecttype.application.result.ObjectTypeDefinition;
import studio.one.platform.objecttype.application.usecase.ObjectTypeKeyRuntimeService;
import studio.one.platform.objecttype.application.usecase.ObjectTypeRuntimeService;
import studio.one.platform.objecttype.application.result.ObjectTypeView;
import studio.one.platform.objecttype.application.result.ObjectTypePolicyView;
import studio.one.platform.objecttype.application.command.ValidateUploadCommand;
import studio.one.platform.objecttype.application.result.ValidateUploadResult;
import studio.one.platform.objecttype.web.dto.request.ValidateUploadRequest;
import studio.one.platform.web.dto.ApiResponse;

class ObjectTypeControllerTest {

    @Test
    void definitionMapsServiceViewToWebDto() {
        ObjectTypeRuntimeService runtimeService = mock(ObjectTypeRuntimeService.class);
        ObjectTypeController controller = new ObjectTypeController(runtimeService);
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-03-30T01:02:03Z");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-03-30T04:05:06Z");

        when(runtimeService.definition(1001)).thenReturn(new ObjectTypeDefinition(
                new ObjectTypeView(1001, "attachment", "Attachment", "media", "active", "desc",
                        "system", 1L, createdAt, "system", 1L, updatedAt),
                new ObjectTypePolicyView(1001, 12, "png", "image/png", "{\"x\":1}",
                        "system", 1L, createdAt, "system", 1L, updatedAt)));

        ResponseEntity<ApiResponse<studio.one.platform.objecttype.web.dto.response.ObjectTypeDefinitionDto>> response =
                controller.definition(1001);

        assertEquals(1001, response.getBody().getData().getType().getObjectType());
        assertEquals("attachment", response.getBody().getData().getType().getCode());
        assertEquals(12, response.getBody().getData().getPolicy().getMaxFileMb());
        verify(runtimeService).definition(eq(1001));
    }

    @Test
    void validateUploadMapsCommandAndResponse() {
        ObjectTypeRuntimeService runtimeService = mock(ObjectTypeRuntimeService.class);
        ObjectTypeController controller = new ObjectTypeController(runtimeService);
        ValidateUploadRequest request = new ValidateUploadRequest("photo.png", "image/png", 1024L);

        when(runtimeService.validateUpload(eq(1200), eq(new ValidateUploadCommand("photo.png", "image/png", 1024L))))
                .thenReturn(new ValidateUploadResult(true, null));

        ResponseEntity<ApiResponse<studio.one.platform.objecttype.web.dto.response.ValidateUploadResponse>> response =
                controller.validateUpload(1200, request);

        assertTrue(response.getBody().getData().isAllowed());
        assertNull(response.getBody().getData().getReason());
        verify(runtimeService).validateUpload(eq(1200), eq(new ValidateUploadCommand("photo.png", "image/png", 1024L)));
    }

    @Test
    void definitionByKeyDelegatesToRuntimeService() {
        ObjectTypeKeyRuntimeService keyRuntimeService = mock(ObjectTypeKeyRuntimeService.class);
        ObjectTypeKeyController controller = new ObjectTypeKeyController(keyRuntimeService);

        when(keyRuntimeService.definitionByKey("workspace-attachment")).thenReturn(new ObjectTypeDefinition(
                new ObjectTypeView(2103, "workspace-attachment", "Workspace Attachment", "workspace", "active", null,
                        null, 0L, null, null, 0L, null),
                null));

        ResponseEntity<ApiResponse<studio.one.platform.objecttype.web.dto.response.ObjectTypeDefinitionDto>> response =
                controller.definitionByKey("workspace-attachment");

        assertEquals(2103, response.getBody().getData().getType().getObjectType());
        assertEquals("workspace-attachment", response.getBody().getData().getType().getCode());
        verify(keyRuntimeService).definitionByKey(eq("workspace-attachment"));
    }

    @Test
    void validateUploadByKeyDelegatesToRuntimeService() {
        ObjectTypeKeyRuntimeService keyRuntimeService = mock(ObjectTypeKeyRuntimeService.class);
        ObjectTypeKeyController controller = new ObjectTypeKeyController(keyRuntimeService);
        ValidateUploadRequest request = new ValidateUploadRequest("photo.png", "image/png", 1024L);

        when(keyRuntimeService.validateUploadByKey(
                eq("workspace-attachment"),
                eq(new ValidateUploadCommand("photo.png", "image/png", 1024L))))
                .thenReturn(new ValidateUploadResult(true, null));

        ResponseEntity<ApiResponse<studio.one.platform.objecttype.web.dto.response.ValidateUploadResponse>> response =
                controller.validateUploadByKey("workspace-attachment", request);

        assertTrue(response.getBody().getData().isAllowed());
        verify(keyRuntimeService).validateUploadByKey(
                eq("workspace-attachment"),
                eq(new ValidateUploadCommand("photo.png", "image/png", 1024L)));
    }
}
