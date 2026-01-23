package studio.one.platform.objecttype.yaml;

import java.util.Collections;
import java.util.Map;

import studio.one.platform.objecttype.model.ObjectPolicy;

/**
 * In-memory policy loaded from YAML.
 */
public class YamlObjectPolicy implements ObjectPolicy {

    private final String policyKey;
    private final Map<String, Object> attributes;

    public YamlObjectPolicy(String policyKey, Map<String, Object> attributes) {
        this.policyKey = policyKey;
        this.attributes = attributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(attributes);
    }

    @Override
    public String getPolicyKey() {
        return policyKey;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
