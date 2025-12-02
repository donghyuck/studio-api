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

    private Map<String, String> prompts = new HashMap<>();
 
}
