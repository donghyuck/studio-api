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
 * YamlApplicationProperties는 Spring의 Environment를 사용하여 YAML 파일에서 애플리케이션 속성을
 * 읽어오는 구현체.
 * 이 클래스는 ApplicationProperties 인터페이스를 구현하며, Map<String, String>
 * 인터페이스도 구현하여 속성에 대한 키-값 쌍을 제공.
 * 
 * @author donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-21  donghyuck, son: 최초 생성.
 *          </pre>
 */
@RequiredArgsConstructor
@Slf4j
public class YamlApplicationProperties implements ApplicationProperties {

    private final Environment environment;

    private final I18n i18n;

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

    @Override
    public boolean getBooleanProperty(String name) {
        // null → false 기본
        return Boolean.TRUE.equals(environment.getProperty(name, Boolean.class));
    }

    @Override
    public boolean getBooleanProperty(String name, boolean defaultValue) {
        return environment.getProperty(name, Boolean.class, defaultValue);
    }

    @Override
    public int getIntProperty(String name, int defaultValue) {
        return environment.getProperty(name, Integer.class, defaultValue);
    }

    @Override
    public long getLongProperty(String name, long defaultValue) {
        return environment.getProperty(name, Long.class, defaultValue);
    }

    @Override
    public String getStringProperty(String name, String defaultValue) {
        return environment.getProperty(name, defaultValue);
    }

    /* ========= Children / Names ========= */

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

    @Override
    public Collection<String> getPropertyNames() {
        return streamAllPropertyNames()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /* ========= Map (read-only) ========= */

    @Override
    public String get(Object key) {
        return (key == null) ? null : environment.getProperty(String.valueOf(key));
    }

    @Override
    public boolean containsKey(Object key) {
        return key != null && environment.containsProperty(String.valueOf(key));
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        LinkedHashSet<Entry<String, String>> set = new LinkedHashSet<>();
        for (String k : getPropertyNames()) {
            set.add(new AbstractMap.SimpleImmutableEntry<>(k, environment.getProperty(k)));
        }
        return Collections.unmodifiableSet(set);
    }

    @Override
    public int size() {
        return getPropertyNames().size();
    }

    @Override
    public boolean isEmpty() {
        return getPropertyNames().isEmpty();
    }

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

    @Override
    public String put(String key, String value) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public String remove(Object key) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public Set<String> keySet() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(getPropertyNames()));
    }

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
