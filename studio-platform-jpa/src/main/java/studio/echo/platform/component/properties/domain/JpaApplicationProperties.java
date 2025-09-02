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
 *      @file JpaApplicationProperties.java
 *      @date 2025
 *
 */
package studio.echo.platform.component.properties.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.component.State;
import studio.echo.platform.component.event.PropertyChangeEvent;
import studio.echo.platform.component.properties.domain.entity.Property;
import studio.echo.platform.constant.Colors;
import studio.echo.platform.constant.MessageCodes;
import studio.echo.platform.service.ApplicationProperties;
import studio.echo.platform.service.I18n;
import studio.echo.platform.util.LogUtils;

/**
 * JpaApplicationProperties는 JPA를 사용하여 애플리케이션 속성을 관리하는 구현체.
 * 이 클래스는 ApplicationProperties 인터페이스를 구현하며, Map<String, String>
 * 인터페이스도 구현하여 속성에 대한 키-값 쌍을 제공.
 * 
 * @author donghyuck, son
 * @since 2025-07-24
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-24  donghyuck, son: 최초 생성.
 *          </pre>
 */

@Slf4j
@Lazy
@RequiredArgsConstructor
@Transactional
public class JpaApplicationProperties implements ApplicationProperties {

    private final EntityManager entityManager;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final I18n i18n;

    // The map of property keys to their values
    private ConcurrentMap<String, String> properties = new ConcurrentHashMap<>();

    // The map of property keys to a boolean indicating if they are encrypted or not
    private Map<String, Boolean> encrypted = new ConcurrentHashMap<>();

    private final AtomicBoolean initFlag = new AtomicBoolean(false);

    @PostConstruct
    protected void initialize() {
        log.info(LogUtils.format(i18n, MessageCodes.Info.COMPONENT_STATE, LogUtils.blue(getClass(), true),
                LogUtils.red(State.INITIALIZING.toString())));
        log.debug("Loading application properties using JPA. ({}, {})", entityManager, applicationEventPublisher);
        if (!initFlag.compareAndSet(false, true)) {
            return;
        }
        try {
            properties.clear();
            encrypted.clear();
            loadProperties(properties);
            properties.forEach((key, value) -> log.info("{} {}",
                    StringUtils.rightPad(Colors.WHITE_BRIGHT + key + Colors.RESET, 30), value));
        } catch (Exception e) {
            initFlag.set(false);
            log.error("Failed to initialize properties from database", e);
        }
        log.info(LogUtils.format(i18n, MessageCodes.Info.COMPONENT_STATE, LogUtils.blue(getClass(), true),
                LogUtils.red(State.INITIALIZED.toString())));
    }

    protected void firePropertyChangeEvent(PropertyChangeEvent.EventType type, String propertyName,
            Map<String, Object> params) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, type, propertyName, params);
        if (applicationEventPublisher != null)
            applicationEventPublisher.publishEvent(event);
    }

    // ------------------------------------------------------------------------------------
    // Jpa Internal Methods :
    // ------------------------------------------------------------------------------------
    
    private void loadProperties(Map<String, String> map) {
        final CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        final CriteriaQuery<Property> query = builder.createQuery(Property.class);
        Root<Property> root = query.from(Property.class);
        query.select(root);
        List<Property> resultList = entityManager.createQuery(query).getResultList();
        Map<String, String> data = resultList.stream().collect(Collectors.toMap(Property::getKey, Property::getValue));
        map.putAll(data);
    }

    private void insertProperty(String name, String value, boolean isEncrypted) {
        log.debug("Inserting property isEncrypted: {}, {}", name, isEncrypted);
        entityManager.persist(new Property(name, value));
    }

    private void updateProperty(String name, String value, boolean isEncrypted) {
        log.debug("Updating property isEncrypted: {}, {}", name, isEncrypted);
        entityManager.createQuery(
                "UPDATE Property p SET p.value = :newValue WHERE p.key = :key")
                .setParameter("newValue", value) // 업데이트할 새로운 값
                .setParameter("key", name) // 조건에 해당하는 키 값
                .executeUpdate(); // 업데이트 쿼리 실행 및 영향을 받은 엔티티의 개수를 반환합니다.
    }

    private void deleteProperty(String name) {
        entityManager.createQuery("DELETE FROM Property p WHERE p.key = :key")
                .setParameter("key", name)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM Property p WHERE p.key = :key")
                .setParameter("key", (new StringBuilder()).append(name).append(".%").toString())
                .executeUpdate();
    }

    // ------------------------------------------------------------------------------------
    // Map Interface Methods :
    // ------------------------------------------------------------------------------------

    @Override
    public int size() {
        return properties.size();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return properties.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return properties.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return properties.containsValue(value);
    }

    @Override
    public Collection<String> values() {
        return Collections.unmodifiableCollection(properties.values());
    }

    @Override
    @Transactional
    public void putAll(Map<? extends String, ? extends String> t) {
        if (t == null || t.isEmpty()) {
            return;
        }
        t.forEach((key, value) -> put(key, value, isEncrypted(key)));
    }

    @Override
    public Set<Map.Entry<String, String>> entrySet() {
        return Collections.unmodifiableSet(properties.entrySet());
    }

    @Override
    public Set<String> keySet() {
        return Collections.unmodifiableSet(properties.keySet());
    }

    @Override
    public String get(Object key) {
        return properties.get(key);
    }

    // ------------------------------------------------------------------------------------
    // Internal Methods :
    // ------------------------------------------------------------------------------------
    /**
     * Indicates the encryption status for the given property.
     * 
     * @param name
     *             The name of the property
     * @return {@code true} if the property exists and is encrypted, otherwise
     *         {@code false}
     */
    boolean isEncrypted(final String name) {
        if (name == null) {
            return false;
        }
        final Boolean isEncrypted = encrypted.get(name);
        return isEncrypted != null && isEncrypted;
    }

    /**
     * Set the encryption status for the given property.
     *
     * @param name
     *                The name of the property
     * @param encrypt
     *                True to encrypt the property, false to decrypt
     * @return {@code true} if the property's encryption status changed, otherwise
     *         {@code false}
     */
    boolean setPropertyEncrypted(String name, boolean encrypt) {
        final boolean encryptionWasChanged = name != null && properties.containsKey(name)
                && isEncrypted(name) != encrypt;
        if (encryptionWasChanged) {
            final String value = get(name);
            put(name, value, encrypt);
        }
        return encryptionWasChanged;
    }

    /**
     * Return all children property names of a parent property as a Collection
     * of String objects. For example, given the properties {@code X.Y.A},
     * {@code X.Y.B}, and {@code X.Y.C}, then the child properties of
     * {@code X.Y} are {@code X.Y.A}, {@code X.Y.B}, and {@code X.Y.C}. The method
     * is not recursive; ie, it does not return children of children.
     *
     * @param parentKey the name of the parent property.
     * @return all child property names for the given parent.
     */
    public Collection<String> getChildrenNames(String parentKey) {
        Collection<String> results = new HashSet<>();
        for (String key : properties.keySet()) {
            if (key.startsWith(parentKey + ".")) {
                if (key.equals(parentKey)) {
                    continue;
                }
                int dotIndex = key.indexOf(".", parentKey.length() + 1);
                if (dotIndex < 1) {
                    if (!results.contains(key)) {
                        results.add(key);
                    }
                } else {
                    String name = parentKey + key.substring(parentKey.length(), dotIndex);
                    results.add(name);
                }
            }
        }
        return results;
    }

    /**
     * Returns all property names as a Collection of String values.
     *
     * @return all property names.
     */
    public Collection<String> getPropertyNames() {
        return properties.keySet();
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
    public String remove(Object key) {
        if (!(key instanceof String)) {
            return null;
        }
        String keyStr = (String) key;
        String value = properties.remove(keyStr);

        if (value != null) {
            deleteProperty(keyStr);
            firePropertyChangeEvent(PropertyChangeEvent.EventType.PROPERTY_DELETEED, keyStr, Collections.emptyMap());
        }

        // Generate event.
        firePropertyChangeEvent(PropertyChangeEvent.EventType.PROPERTY_DELETEED, keyStr, Collections.emptyMap());
        return value;
    }

    private String removeWithoutTransaction(String key) {
        String value = properties.remove(key);
        if (value != null) {
            deleteProperty(key);
        }
        return value;
    }

    void localRemove(String key) {
        properties.remove(key);
        // Also remove any children.
        Collection<String> propNames = getPropertyNames();
        for (String name : propNames) {
            if (name.startsWith(key + ".")) {
                properties.remove(name);
            }
        }
        // Generate event.
        firePropertyChangeEvent(PropertyChangeEvent.EventType.PROPERTY_DELETEED, key, Collections.emptyMap());
    }

    /**
     * Saves a property, optionally encrypting it
     * 
     * @param key
     *                    The name of the property
     * @param value
     *                    The value of the property
     * @param isEncrypted
     *                    {@code true} to encrypt the property, {@code true} to
     *                    leave in plain text
     * @return The previous value associated with {@code key}, or {@code null} if
     *         there was no mapping for
     *         {@code key}.
     */

    @Transactional(propagation = Propagation.REQUIRED)
    public String put(String key, String value, boolean isEncrypted) {
        if (key == null) {
            throw new NullPointerException("Key cannot be null. Key=" + key + ", value=" + value);
        }
        if (value == null) {
            // This is the same as deleting, so remove it.
            return removeWithoutTransaction(key);
        }

        key = key.trim();
        if (key.endsWith(".")) {
            key = key.substring(0, key.length() - 1);
        }

        String result;
        synchronized (this) {
            if (properties.containsKey(key)) {
                updateProperty(key, value, isEncrypted);
            } else {
                insertProperty(key, value, isEncrypted);
            }

            result = properties.put(key, value);
            encrypted.put(key, isEncrypted);
        }
        // Generate event.
        Map<String, Object> params = new HashMap<>();
        params.put("value", value);
        firePropertyChangeEvent(PropertyChangeEvent.EventType.PROPERTY_SET, key, params);
        return result;
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
    public String put(String key, String value) {
        return put(key, value, isEncrypted(key));
    }

    void localPut(String key, String value, boolean isEncrypted) {
        properties.put(key, value);
        encrypted.put(key, isEncrypted);
        // Generate event.
        Map<String, Object> params = new HashMap<>();
        params.put("value", value);
        firePropertyChangeEvent(PropertyChangeEvent.EventType.PROPERTY_SET, key, params);
    }

    public String getProperty(String name, String defaultValue) {
        String value = properties.get(name);
        if (value != null) {
            return value;
        } else {
            return defaultValue;
        }
    }

    public boolean getBooleanProperty(String name) {
        return Boolean.valueOf(get(name));
    }

    public boolean getBooleanProperty(String name, boolean defaultValue) {
        String value = get(name);
        if (value != null) {
            return Boolean.valueOf(value);
        } else {
            return defaultValue;
        }
    }

    @Override
    public int getIntProperty(String name, int defaultValue) {
        return NumberUtils.toInt(get(name), defaultValue);
    }

    @Override
    public long getLongProperty(String name, long defaultValue) {
        return NumberUtils.toLong(get(name), defaultValue);
    }

    @Override
    public String getStringProperty(String name, String defaultValue) {
        return StringUtils.defaultString(get(name), defaultValue);
    }

}
