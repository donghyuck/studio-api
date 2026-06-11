package studio.one.platform.ai.web.controller;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.ai.autoconfigure.config.AiEmbeddingOption;
import studio.one.platform.ai.autoconfigure.config.AiEmbeddingOptionCatalog;
import studio.one.platform.constant.PropertyKeys;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = PropertyKeys.AI.Endpoints.PREFIX, name = "enabled", havingValue = "true")
@RequestMapping("${" + PropertyKeys.AI.Endpoints.BASE_PATH + ":/api/ai}")
public class AiProviderMgmtController {

    private final AiEmbeddingOptionCatalog embeddingOptionCatalog;

    @GetMapping("/embedding-options")
    public EmbeddingOptionsResponse embeddingOptions() {
        List<AiEmbeddingOption> options = embeddingOptionCatalog.options();
        return new EmbeddingOptionsResponse(options);
    }

    public record EmbeddingOptionsResponse(List<AiEmbeddingOption> options) {

        public EmbeddingOptionsResponse {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }
}
