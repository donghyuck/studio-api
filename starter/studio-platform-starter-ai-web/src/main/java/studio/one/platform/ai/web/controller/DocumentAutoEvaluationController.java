package studio.one.platform.ai.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.ai.web.dto.DocumentAutoEvaluationRequestDto;
import studio.one.platform.ai.web.dto.DocumentAutoEvaluationResponseDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.MGMT_BASE_PATH + ":/api/mgmt/ai}/rag")
public class DocumentAutoEvaluationController {
    private final DocumentAutoEvaluationService service;

    public DocumentAutoEvaluationController(DocumentAutoEvaluationService service) {
        this.service = java.util.Objects.requireNonNull(service, "service");
    }

    @PostMapping("/objects/{objectType}/{objectId}/evaluations/auto")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')"
            + " and (!@ragIndexJobEndpointSecurity.isAttachmentObject(#objectType)"
            + " or @endpointAuthz.can('features:attachment','read'))")
    public ResponseEntity<ApiResponse<DocumentAutoEvaluationResponseDto>> evaluate(
            @PathVariable("objectType") String objectType,
            @PathVariable("objectId") String objectId,
            @RequestBody(required = false) DocumentAutoEvaluationRequestDto request) {
        return ResponseEntity.ok(ApiResponse.ok(service.evaluate(objectType, objectId, request)));
    }
}
