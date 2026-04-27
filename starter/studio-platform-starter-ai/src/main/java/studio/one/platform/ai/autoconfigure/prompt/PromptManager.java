package studio.one.platform.ai.autoconfigure.prompt;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import studio.one.platform.ai.service.prompt.PromptRenderer;

public class PromptManager implements PromptRenderer {

    private final Map<String, String> prompts;
    private final ResourceLoader resourceLoader;
    private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();
    private final Map<String, Mustache> templateCache = new ConcurrentHashMap<>();

    public PromptManager(Map<String, String> prompts, ResourceLoader resourceLoader) {
        this.prompts = prompts;
        this.resourceLoader = resourceLoader;
    }

    private Mustache loadTemplate(String name) {
        return templateCache.computeIfAbsent(name, key -> {
            try {
                String location = getLocation(key);
                Resource resource = resourceLoader.getResource(location);
                try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    return mustacheFactory.compile(reader, key);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to load prompt template: " + key, e);
            }
        });
    }

    private String getLocation(String name) {
        String loc = prompts.get(name);
        if (loc == null) {
            throw new IllegalArgumentException("Unknown prompt name: " + name);
        }
        return loc;
    }

    @Override
    public String render(String name, Map<String, Object> params) {
        Mustache template = loadTemplate(name);
        StringWriter writer = new StringWriter();
        template.execute(writer, params);
        return writer.toString();
    }

    @Override
    public String getRawPrompt(String name) {
        return render(name, Map.of());
    }
}
