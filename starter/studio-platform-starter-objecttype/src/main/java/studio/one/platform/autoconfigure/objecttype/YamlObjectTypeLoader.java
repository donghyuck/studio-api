package studio.one.platform.autoconfigure.objecttype;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import studio.one.platform.objecttype.model.ObjectPolicy;
import studio.one.platform.objecttype.model.ObjectTypeMetadata;
import studio.one.platform.objecttype.infrastructure.yaml.YamlObjectPolicy;
import studio.one.platform.objecttype.infrastructure.yaml.YamlObjectTypeMetadata;

public class YamlObjectTypeLoader {

    private static final Logger log = LoggerFactory.getLogger(YamlObjectTypeLoader.class);

    public static final class Result {
        private final Map<Integer, ObjectTypeMetadata> byType;
        private final Map<String, ObjectTypeMetadata> byKey;
        private final Map<Integer, ObjectPolicy> policies;

        public Result(
                Map<Integer, ObjectTypeMetadata> byType,
                Map<String, ObjectTypeMetadata> byKey,
                Map<Integer, ObjectPolicy> policies) {
            this.byType = byType;
            this.byKey = byKey;
            this.policies = policies;
        }

        public Map<Integer, ObjectTypeMetadata> byType() {
            return byType;
        }

        public Map<String, ObjectTypeMetadata> byKey() {
            return byKey;
        }

        public Map<Integer, ObjectPolicy> policies() {
            return policies;
        }
    }

    private final ResourceLoader resourceLoader;

    public YamlObjectTypeLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public Result load(String location) {
        if (!StringUtils.hasText(location)) {
            log.debug("ObjectType YAML location is empty; returning empty registry");
            return empty();
        }
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.info("ObjectType YAML resource not found: {}", location);
            return empty();
        }
        try (InputStream in = resource.getInputStream()) {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(50);
            Yaml yaml = new Yaml(new SafeConstructor(options));
            Object data = yaml.load(in);
            if (!(data instanceof Map<?, ?>)) {
                log.warn("ObjectType YAML root is not a map: {}", location);
                return empty();
            }
            Map<?, ?> root = (Map<?, ?>) data;
            Object list = root.get("objecttypes");
            if (!(list instanceof List<?>)) {
                log.warn("ObjectType YAML has no 'objecttypes' list: {}", location);
                return empty();
            }
            List<?> items = (List<?>) list;
            Map<Integer, ObjectTypeMetadata> byType = new HashMap<>();
            Map<String, ObjectTypeMetadata> byKey = new HashMap<>();
            Map<Integer, ObjectPolicy> policies = new HashMap<>();
            for (Object item : items) {
                if (!(item instanceof Map<?, ?>)) {
                    continue;
                }
                Map<?, ?> m = (Map<?, ?>) item;
                Integer type = getInt(m, "type", "objectType");
                if (type == null) {
                    continue;
                }
                String key = getString(m, "key", "code");
                String name = getString(m, "name");
                Map<String, Object> attrs = new HashMap<>();
                putIfPresent(attrs, "domain", m.get("domain"));
                putIfPresent(attrs, "status", m.get("status"));
                putIfPresent(attrs, "description", m.get("description"));
                ObjectTypeMetadata meta = new YamlObjectTypeMetadata(type, key, name, attrs);
                byType.put(type, meta);
                if (StringUtils.hasText(key)) {
                    byKey.put(key, meta);
                }
                Object policyRaw = m.get("policy");
                if (policyRaw instanceof Map<?, ?>) {
                    Map<?, ?> p = (Map<?, ?>) policyRaw;
                    String policyKey = getString(p, "key");
                    Map<String, Object> policyAttrs = new HashMap<>();
                    for (Map.Entry<?, ?> e : p.entrySet()) {
                        if ("key".equals(String.valueOf(e.getKey()))) {
                            continue;
                        }
                        putIfPresent(policyAttrs, String.valueOf(e.getKey()), e.getValue());
                    }
                    ObjectPolicy policy = new YamlObjectPolicy(
                            StringUtils.hasText(policyKey) ? policyKey : "objecttype:" + type,
                            policyAttrs);
                    policies.put(type, policy);
                }
            }
            return new Result(
                    Collections.unmodifiableMap(byType),
                    Collections.unmodifiableMap(byKey),
                    Collections.unmodifiableMap(policies));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read objecttype YAML: " + location, ex);
        }
    }

    private static Result empty() {
        return new Result(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    private static Integer getInt(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v instanceof Number) {
                return ((Number) v).intValue();
            }
            if (v instanceof String && StringUtils.hasText((String) v)) {
                try {
                    return Integer.parseInt(((String) v).trim());
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }
        }
        return null;
    }

    private static String getString(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null) {
                String s = String.valueOf(v);
                if (StringUtils.hasText(s)) {
                    return s;
                }
            }
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String && !StringUtils.hasText((String) value)) {
            return;
        }
        target.put(key, value);
    }
}
