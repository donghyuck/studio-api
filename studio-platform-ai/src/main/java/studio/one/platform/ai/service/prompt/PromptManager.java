package studio.one.platform.ai.service.prompt;

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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PromptManager {

    private final Map<String, String> prompts;
    private final ResourceLoader resourceLoader;
    private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

    // prompt 이름 -> 컴파일된 템플릿
    private final Map<String, Mustache> templateCache = new ConcurrentHashMap<>();

    public PromptManager(Map<String, String> prompts, ResourceLoader resourceLoader) {
        this.prompts = prompts;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 프롬프트 템플릿을 로딩/컴파일해서 캐시에 저장하고 반환.
     */
    private Mustache loadTemplate(String name) { 
        return templateCache.computeIfAbsent(name, key -> {
            try {
                String location = getLocation(key);
                 Resource resource = resourceLoader.getResource(location);
                try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    // Mustache의 template 이름은 key 로 사용
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

    /**
     * name: application.yml 에 적어둔 프롬프트 논리 이름 (예: "query-rewrite")
     * params: 템플릿 변수 맵 (예: {{user_query}}, {{original_query}} 등)
     */
    public String render(String name, Map<String, Object> params) {
        Mustache template = loadTemplate(name);
        StringWriter writer = new StringWriter();
        template.execute(writer, params);
        return writer.toString();
    }

    /**
     * 변수 없이 원본 프롬프트 텍스트만 받고 싶을 때
     */
    public String getRawPrompt(String name) {
        return render(name, Map.of());
    }
    
}
