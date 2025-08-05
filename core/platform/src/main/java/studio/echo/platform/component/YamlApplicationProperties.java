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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.constant.Colors;
import studio.echo.platform.constant.MessageCodes;
import studio.echo.platform.service.ApplicationProperties;
import studio.echo.platform.service.I18n;
import studio.echo.platform.util.LogUtil;

/**
 * YamlApplicationProperties는 Spring의 Environment를 사용하여 YAML 파일에서 애플리케이션 속성을 읽어오는 구현체.
 * 이 클래스는 ApplicationProperties 인터페이스를 구현하며, Map<String, String>
 * 인터페이스도 구현하여 속성에 대한 키-값 쌍을 제공.
 * 
 * @author  donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-21  donghyuck, son: 최초 생성.
 * </pre>
 */
@RequiredArgsConstructor
@Slf4j
public class YamlApplicationProperties implements ApplicationProperties {

    private final Environment environment;

    private final I18n i18n;

     @PostConstruct
    protected void initialize() {

        log.info(LogUtil.format(i18n, MessageCodes.Info.COMPONENT_STATE, LogUtil.blue(getClass(), true), LogUtil.red(State.INITIALIZING.toString())));
        log.debug("Loading application properties using yml.");
 
        log.info(LogUtil.format(i18n, MessageCodes.Info.COMPONENT_STATE, LogUtil.blue(getClass(), true), LogUtil.red(State.INITIALIZED.toString())));
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
        return Collections.emptyList();  
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
