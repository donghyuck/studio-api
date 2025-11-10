package studio.one.platform.component.properties.domain.service;

import java.sql.Types;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.SqlParameterValue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.component.event.PropertyChangeEvent;
import studio.one.platform.constant.Colors;
import studio.one.platform.service.ApplicationProperties;
import studio.one.platform.service.I18n;

/**
 *
 * @author  donghyuck, son
 * @since 2025-11-09
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-09  donghyuck, son: 최초 생성.
 * </pre>
 */

@Slf4j
@Lazy
@RequiredArgsConstructor
@Transactional
public class JdbcApplicationProperties implements ApplicationProperties {

     protected final AtomicBoolean initFlag = new AtomicBoolean(false);
     
     private final JdbcTemplate jdbcTemplate;
     private final ApplicationEventPublisher applicationEventPublisher;
     private final I18n i18n;


    // The map of property keys to their values
    private final ConcurrentMap<String, String> properties = new ConcurrentHashMap<>();

    // The map of property keys to a boolean indicating if they are encrypted or not
    private final Map<String, Boolean> encrypted = new ConcurrentHashMap<>();


    /**
     * For internal use only. This method allows for the reloading of all properties
     * from the
     * values in the database. This is required since it's quite possible during the
     * setup
     * process that a database connection will not be available till after this
     * class is
     * initialized. Thus, if there are existing properties in the database we will
     * want to reload
     * this class after the setup process has been completed.
     */
    @PostConstruct
    protected void initialize() {
        log.info("Loading application properties from database.");
        if (initFlag.compareAndSet(false, true)) {
            try {
                log.info("Loading application properties from database.");
                properties.clear();
                encrypted.clear();
                loadProperties(properties);
                properties.forEach((key, value) -> log.info("{}............{}", Colors.GREEN + key + Colors.RESET, value));
            } catch (Exception e) {
                log.error("Failed to initialize properties from database", e);
                initFlag.set(false); // 실패하면 초기화 상태를 되돌림
            }
        }
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
    public void putAll(Map<? extends String, ? extends String> t) {
        if (t == null || t.isEmpty()) {
            return;
        }
        Map<String, String> updates = new HashMap<>();
        t.forEach((key, value) -> {
            if (key != null) {
                String trimmedKey = key.trim();
                if (trimmedKey.endsWith(".")) {
                    trimmedKey = trimmedKey.substring(0, trimmedKey.length() - 1);
                }
                updates.put(trimmedKey, value);
            }
        });
        properties.putAll(updates);
        
        // 데이터베이스 반영을 위해 일괄 insert/update 실행
        updates.forEach((key, value) -> put(key, value, isPropertyEncrypted(key)));
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
    public boolean isPropertyEncrypted(String name) {
        return name != null && Boolean.TRUE.equals(encrypted.get(name));
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
                && isPropertyEncrypted(name) != encrypt;
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

    @Override
    public String remove(Object key) {
        if (!(key instanceof String)) {
            return null;
        }
        String keyStr = (String) key;
        String value = properties.remove(keyStr);
        if (value != null) {
            deleteProperty(keyStr);
            firePropertyChangeEvent(PropertyChangeEvent.EventType.PROPERTY_DELETED, keyStr, Collections.emptyMap());
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
        firePropertyChangeEvent(PropertyChangeEvent.EventType.PROPERTY_DELETED, key, Collections.emptyMap());
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
    public String put(String key, String value, boolean isEncrypted) {
        if (value == null) {
            // This is the same as deleting, so remove it.
            return remove(key);
        }
        if (key == null) {
            throw new NullPointerException("Key cannot be null. Key=" + key + ", value=" + value);
        }
        if (key.endsWith(".")) {
            key = key.substring(0, key.length() - 1);
        }
        key = key.trim();
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

    @Override
    public String put(String key, String value) {
        return put(key, value, isPropertyEncrypted(key));
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

    private void insertProperty(String name, String value, boolean isEncrypted) {
        log.debug("Inserting property isEncrypted: {}, {}", name, isEncrypted);
        jdbcTemplate.update(SQL_INSERT_PROPERTY,
                new SqlParameterValue(Types.VARCHAR, name),
                new SqlParameterValue(Types.VARCHAR, value));
    }

    private void updateProperty(String name, String value, boolean isEncrypted) {
        log.debug("Updating property isEncrypted: {}, {}", name, isEncrypted);
        jdbcTemplate.update(SQL_UPDATE_PROPERTY,
                new SqlParameterValue(Types.VARCHAR, value),
                new SqlParameterValue(Types.VARCHAR, name));
    }

    private void deleteProperty(String name) {
        jdbcTemplate.update(SQL_DELETE_PROPERTY, new SqlParameterValue(Types.VARCHAR, name));
        jdbcTemplate.update(SQL_DELETE_PROPERTY, new SqlParameterValue(Types.VARCHAR, (new StringBuilder()).append(name).append(".%").toString()));
    }

    protected void firePropertyChangeEvent(PropertyChangeEvent.EventType type, String propertyName,  Map<String, Object> params) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, type, propertyName, params);
        if (applicationEventPublisher != null)
            applicationEventPublisher.publishEvent(event);
    }
    // ------------------------------------------------------------------------------------
    // JDBC Internal Methods :
    // ------------------------------------------------------------------------------------
    private void loadProperties(Map<String, String> map) {
        Map<String, String> result = jdbcTemplate.query(
                SQL_SELECT_ALL_PROPERTY,
                (ResultSetExtractor<Map<String, String>>) rs -> {
                    Map<String, String> tempMap = new HashMap<>();
                    while (rs.next()) {
                        String name = rs.getString(1);
                        String value = rs.getString(2);
                        tempMap.put(name, " ".equals(value) ? "" : value);
                    }
                    return tempMap;
                });
        map.putAll(result);
    }

    private static final String SQL_INSERT_PROPERTY = """
     INSERT INTO TB_APPLICATION_PROPERTY ( PROPERTY_NAME, PROPERTY_VALUE ) VALUES (? ,?) 
     """; 
    private static final String SQL_UPDATE_PROPERTY = """
    UPDATE TB_APPLICATION_PROPERTY SET PROPERTY_VALUE=? WHERE PROPERTY_NAME=?
     """; 
    private static final String SQL_DELETE_PROPERTY = """
     DELETE FROM TB_APPLICATION_PROPERTY 
     WHERE PROPERTY_NAME = ?
     """;                             
    private static final String SQL_SELECT_ALL_PROPERTY = """
     SELECT PROPERTY_NAME, PROPERTY_VALUE 
     FROM TB_APPLICATION_PROPERTY
     """;               
}