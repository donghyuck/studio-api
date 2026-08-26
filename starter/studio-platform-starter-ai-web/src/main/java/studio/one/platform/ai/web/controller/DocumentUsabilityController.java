package studio.one.platform.ai.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.MGMT_BASE_PATH + ":/api/mgmt/ai}/rag")
public class DocumentUsabilityController {
    private final DocumentUsabilityService service;

    public DocumentUsabilityController(DocumentUsabilityService service) {
        this.service = java.util.Objects.requireNonNull(service, "service");
    }

    @GetMapping("/objects/{objectType}/{objectId}/usability")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')"
            + " and @ragObjectAuthorizationRouter.canRead(#objectType, #objectId)")
    public ResponseEntity<ApiResponse<DocumentUsabilityAssessment>> usability(
            @PathVariable("objectType") String objectType,
            @PathVariable("objectId") String objectId) {
        return ResponseEntity.ok(ApiResponse.ok(service.evaluate(objectType, objectId)));
    }
}
