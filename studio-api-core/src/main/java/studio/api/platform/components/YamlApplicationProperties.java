package studio.api.platform.components;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import studio.api.platform.util.Constants;
import studio.echoes.platform.service.ApplicationProperties;

@ConditionalOnProperty(value = Constants.COMPONENTS_PRPPERTY_ENABLED , havingValue = "false", matchIfMissing = true)
@Component(ApplicationProperties.SERVICE_NAME)
public class YamlApplicationProperties implements ApplicationProperties {

    private final Environment environment;

    public YamlApplicationProperties(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean getBooleanProperty(String name) {
        return Boolean.parseBoolean(environment.getProperty(name));
    }

    @Override
    public boolean getBooleanProperty(String name, boolean defaultValue) {
        return Boolean.parseBoolean(environment.getProperty(name, String.valueOf(defaultValue)));
    }

    @Override
    public Collection<String> getChildrenNames(String name) {
        return Collections.emptyList(); // 또는 ConfigurationPropertySources 사용
    }

    @Override
    public int getIntProperty(String name, int defaultValue) {
        return Integer.parseInt(environment.getProperty(name, String.valueOf(defaultValue)));
    }

    @Override
    public long getLongProperty(String name, long defaultValue) {
        return Long.parseLong(environment.getProperty(name, String.valueOf(defaultValue)));
    }

    @Override
    public Collection<String> getPropertyNames() {
        return Collections.emptyList(); // 확장 가능
    }

    @Override
    public String getStringProperty(String name, String defaultValue) {
        return environment.getProperty(name, defaultValue);
    }

    // Map 인터페이스 구현 (부분만 지원)
    @Override
    public String get(Object key) {
        return environment.getProperty(key.toString());
    }

    @Override
    public boolean containsKey(Object key) {
        return environment.containsProperty(key.toString());
    }

    // 아래 메서드들은 필요에 따라 구현
    @Override public Set<Entry<String, String>> entrySet() { throw new UnsupportedOperationException(); }
    @Override public int size() { throw new UnsupportedOperationException(); }
    @Override public boolean isEmpty() { throw new UnsupportedOperationException(); }
    @Override public boolean containsValue(Object value) { throw new UnsupportedOperationException(); }
    @Override public String put(String key, String value) { throw new UnsupportedOperationException(); }
    @Override public String remove(Object key) { throw new UnsupportedOperationException(); }
    @Override public void putAll(Map<? extends String, ? extends String> m) { throw new UnsupportedOperationException(); }
    @Override public void clear() { throw new UnsupportedOperationException(); }
    @Override public Set<String> keySet() { throw new UnsupportedOperationException(); }
    @Override public Collection<String> values() { throw new UnsupportedOperationException(); }
}

