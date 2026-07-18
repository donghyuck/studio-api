package studio.one.platform.ai.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.BASE_PATH + ":/api/ai}/usage")
public class AiModelUsageController {

    private final AiModelUsageStore usageStore;

    public AiModelUsageController(AiModelUsageStore usageStore) {
        this.usageStore = usageStore;
    }

    @GetMapping("/models")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','read')")
    public ResponseEntity<ApiResponse<List<AiModelUsageStore.ModelUsageSummary>>> models(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model) {
        return ResponseEntity.ok(ApiResponse.ok(usageStore.summaries(provider, model)));
    }
}
