package studio.api.platform.components;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import studio.api.platform.components.mapper.ApplicationPropertiesDelegate;
import studio.api.platform.i18n.PlatformLogLocalizer;
import studio.api.platform.spring.autoconfigure.condition.ConditionalOnProperties;
import studio.api.platform.util.Constants;
import studio.api.platform.util.LogUtils;
import studio.echoes.platform.component.State;
import studio.echoes.platform.constant.Colors;
import studio.echoes.platform.service.ApplicationProperties;

@ConditionalOnProperties(
    value = {
        @ConditionalOnProperties.Property(name = Constants.COMPONENTS_PRPPERTY_ENABLED, havingValue = "true", matchIfMissing = false),
        @ConditionalOnProperties.Property(name = Constants.COMPONENTS_PRPPERTY_PERSISTENCE_MYBATIS_ENABLED, havingValue = "true", matchIfMissing = false)
    }
)
@Component(ApplicationProperties.SERVICE_NAME)
@Slf4j
@DependsOn(ApplicationPropertiesDelegate.SERVICE_NAME)
public class JdbcApplicationProperties implements ApplicationProperties {

    private final ApplicationPropertiesDelegate applicationPropertiesMapper;
    private final Map<String, String> properties = new ConcurrentHashMap<>();

    public JdbcApplicationProperties(@Qualifier(ApplicationPropertiesDelegate.SERVICE_NAME) ApplicationPropertiesDelegate applicationPropertiesMapper) {
        this.applicationPropertiesMapper = applicationPropertiesMapper;
    }

    @PostConstruct
    public void initialize() {
        log.info(LogUtils.logComponentState(ApplicationProperties.class, State.INITIALIZING, true ));
        boolean hasError = false ;
        try{
            loadProperties();
        }catch(DataAccessException e){
            hasError = true;
            log.warn( LogUtils.logComponentDisabled(getClass(), true), e );
            ifConnectionFail(e);
        }
        log.info(LogUtils.logComponentIntitalized(ApplicationProperties.class, State.INITIALIZED, hasError,  true ));
    }

    private void ifConnectionFail(DataAccessException e){
        if( e.getRootCause() instanceof java.net.SocketTimeoutException ){
            log.error( Colors.format(Colors.RED_BOLD, PlatformLogLocalizer.getInstance().getMessage(PlatformLogLocalizer.MessageCode.DATABASE_CONNECTION_FAIL.code())));
        }
    }

    private void loadProperties() {
        List<Map<String, String>> list = applicationPropertiesMapper.selectAll();
        list.forEach(row -> {
            String key =  row.get("PROPERTY_NAME");
            String value =  row.get("PROPERTY_VALUE");
            properties.put(key, value);
        });
    }

    @Override
    public int size() {
        return properties.size();
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
    public String get(Object key) {
        return properties.get(key);
    }

    @Override
    public String put(String key, String value) {
        boolean exists = properties.containsKey(key);
        properties.put(key, value);
        if (exists)
            applicationPropertiesMapper.updateProperty(key, value);
        else
            applicationPropertiesMapper.insertProperty(key, value);
        return value;
    }

    @Override
    public String remove(Object key) {
        if (!(key instanceof String)) return null;
        String name = (String) key;
        String old = properties.remove(name);
        if (old != null) {
            applicationPropertiesMapper.deleteProperty(name);
        }
        return old;
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
        m.forEach(this::put);
    }

    @Override
    public void clear() {
        properties.keySet().forEach(applicationPropertiesMapper::deleteProperty);
        properties.clear();
    }

    @Override
    public Set<String> keySet() {
        return Collections.unmodifiableSet(properties.keySet());
    }

    @Override
    public Collection<String> values() {
        return Collections.unmodifiableCollection(properties.values());
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        return Collections.unmodifiableSet(properties.entrySet());
    }

    @Override
    public boolean getBooleanProperty(String name) {
        return Boolean.parseBoolean(properties.get(name));
    }

    @Override
    public boolean getBooleanProperty(String name, boolean defaultValue) {
        String value = properties.get(name);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    @Override
    public int getIntProperty(String name, int defaultValue) {
        try {
            return Integer.parseInt(properties.get(name));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public long getLongProperty(String name, long defaultValue) {
        try {
            return Long.parseLong(properties.get(name));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public String getStringProperty(String name, String defaultValue) {
        return properties.getOrDefault(name, defaultValue);
    }

    @Override
    public Collection<String> getChildrenNames(String parentKey) {
        return properties.keySet().stream()
                .filter(k -> k.startsWith(parentKey + ".") && !k.equals(parentKey))
                .collect(Collectors.toSet());
    }

    @Override
    public Collection<String> getPropertyNames() {
        return Collections.unmodifiableSet(properties.keySet());
    }
}
