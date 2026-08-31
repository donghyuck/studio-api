package studio.one.platform.ai.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.ai.web.dto.DocumentQuestionSuggestionsResponseDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.MGMT_BASE_PATH + ":/api/mgmt/ai}/rag")
public class DocumentQuestionSuggestionController {

    private final DocumentQuestionSuggestionService service;

    public DocumentQuestionSuggestionController(DocumentQuestionSuggestionService service) {
        this.service = java.util.Objects.requireNonNull(service, "service");
    }

    @GetMapping("/objects/{objectType}/{objectId}/question-suggestions")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')"
            + " and @ragObjectAuthorizationRouter.canRead(#objectType, #objectId)")
    public ResponseEntity<ApiResponse<DocumentQuestionSuggestionsResponseDto>> suggestions(
            @PathVariable("objectType") String objectType,
            @PathVariable("objectId") String objectId) {
        return ResponseEntity.ok(ApiResponse.ok(service.suggest(objectType, objectId)));
    }
}
