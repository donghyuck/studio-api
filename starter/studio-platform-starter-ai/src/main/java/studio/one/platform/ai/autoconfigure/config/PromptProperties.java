package studio.one.platform.ai.autoconfigure.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.AI.PREFIX )
@Getter @Setter
public class PromptProperties {

    private Map<String, String> prompts = defaultPrompts();

    private static Map<String, String> defaultPrompts() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("keyword-extraction", "classpath:/prompts/keyword-extraction.v1.prompt");
        defaults.put("query-rewrite", "classpath:/prompts/query-rewrite.v1.prompt");
        defaults.put("rag-cleaner", "classpath:/prompts/rag-cleaner.v1.prompt");
        defaults.put("summarization", "classpath:/prompts/summarization.v1.prompt");
        return defaults;
    }
 
}
