/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file YamlApplicationProperties.java
 *      @date 2025
 *
 */
package studio.echo.platform.component;

import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.constant.MessageCodes;
import studio.echo.platform.service.ApplicationProperties;
import studio.echo.platform.service.I18n;
import studio.echo.platform.util.LogUtils;

/**
 * An implementation of {@link ApplicationProperties} that reads application
 * properties from YAML files using Spring's {@link Environment}.
 * <p>
 * This class implements both the {@link ApplicationProperties} and
 * {@link Map<String, String>} interfaces, providing key-value pairs for
 * properties. It acts as a read-only map.
 *
 * @author donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 */
@RequiredArgsConstructor
@Slf4j
public class YamlApplicationProperties implements ApplicationProperties {

    private final Environment environment;

    private final I18n i18n;

    /**
     * Initializes the component after construction. This method is called by Spring
     * and logs the initialization state.
     */
    @PostConstruct
    protected void initialize() {

        String comp = LogUtils.blue(getClass(), true);
        try {
            log.info(LogUtils.format(i18n, MessageCodes.Info.COMPONENT_STATE, comp,
                    LogUtils.red(State.INITIALIZING.toString())));
            log.debug("Loading application properties via Spring Environment (YAML backing).");
            log.info(LogUtils.format(i18n, MessageCodes.Info.COMPONENT_STATE, comp,
                    LogUtils.red(State.INITIALIZED.toString())));
        } catch (Exception e) {
            log.info("[YamlApplicationProperties] INITIALIZED");
        }
    }
    /* ========= Typed getters ========= */

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getBooleanProperty(String name) {
        // null → false 기본
        return Boolean.TRUE.equals(environment.getProperty(name, Boolean.class));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getBooleanProperty(String name, boolean defaultValue) {
        return environment.getProperty(name, Boolean.class, defaultValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getIntProperty(String name, int defaultValue) {
        return environment.getProperty(name, Integer.class, defaultValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLongProperty(String name, long defaultValue) {
        return environment.getProperty(name, Long.class, defaultValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStringProperty(String name, String defaultValue) {
        return environment.getProperty(name, defaultValue);
    }

    /* ========= Children / Names ========= */

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getChildrenNames(String prefix) {
        if (prefix == null || prefix.isEmpty())
            return Collections.emptySet();
        String normalized = prefix.endsWith(".") ? prefix : prefix + ".";
        int baseLen = normalized.length();

        return streamAllPropertyNames()
                .filter(k -> k.startsWith(normalized))
                .map(k -> {
                    String rest = k.substring(baseLen);
                    int dot = rest.indexOf('.');
                    return (dot >= 0) ? rest.substring(0, dot) : rest;
                })
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getPropertyNames() {
        return streamAllPropertyNames()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /* ========= Map (read-only) ========= */

    /**
     * {@inheritDoc}
     */
    @Override
    public String get(Object key) {
        return (key == null) ? null : environment.getProperty(String.valueOf(key));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(Object key) {
        return key != null && environment.containsProperty(String.valueOf(key));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Entry<String, String>> entrySet() {
        LinkedHashSet<Entry<String, String>> set = new LinkedHashSet<>();
        for (String k : getPropertyNames()) {
            set.add(new AbstractMap.SimpleImmutableEntry<>(k, environment.getProperty(k)));
        }
        return Collections.unmodifiableSet(set);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return getPropertyNames().size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return getPropertyNames().isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsValue(Object value) {
        if (value == null)
            return false;
        for (String k : getPropertyNames()) {
            if (Objects.equals(environment.getProperty(k), value))
                return true;
        }
        return false;
    }

    /**
     * @throws UnsupportedOperationException always
     */
    @Override
    public String put(String key, String value) {
        throw new UnsupportedOperationException("read-only");
    }

    /**
     * @throws UnsupportedOperationException always
     */
    @Override
    public String remove(Object key) {
        throw new UnsupportedOperationException("read-only");
    }

    /**
     * @throws UnsupportedOperationException always
     */
    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
        throw new UnsupportedOperationException("read-only");
    }

    /**
     * @throws UnsupportedOperationException always
     */
    @Override
    public void clear() {
        throw new UnsupportedOperationException("read-only");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> keySet() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(getPropertyNames()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> values() {
        List<String> vals = new ArrayList<>();
        for (String k : getPropertyNames())
            vals.add(environment.getProperty(k));
        return Collections.unmodifiableList(vals);
    }

    /* ========= Helpers ========= */
    private java.util.stream.Stream<String> streamAllPropertyNames() {
        if (!(environment instanceof ConfigurableEnvironment))
            return java.util.stream.Stream.empty();
        ConfigurableEnvironment ce = (ConfigurableEnvironment) environment;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (PropertySource<?> ps : ce.getPropertySources()) {
            if (ps instanceof EnumerablePropertySource) {
                String[] arr = ((EnumerablePropertySource<?>) ps).getPropertyNames();
                if (arr != null)
                    Collections.addAll(names, arr);
            }
        }
        return names.stream();
    }
}
