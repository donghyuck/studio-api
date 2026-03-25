package studio.one.platform.ai.service.prompt;

import java.util.Map;

public interface PromptRenderer {

    String render(String name, Map<String, Object> params);

    String getRawPrompt(String name);
}
