package studio.one.platform.autoconfigure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import studio.one.platform.constant.PropertyKeys;

@SuppressWarnings("deprecation")
public class EnvironmentPersistenceTypeResolver implements PersistenceTypeResolver {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentPersistenceTypeResolver.class);

    private final Environment environment;

    public EnvironmentPersistenceTypeResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public StudioPersistenceType resolve(String featureName, StudioPersistenceType defaultType) {
        if (StringUtils.hasText(featureName)) {
            String featureKey = featurePropertyKey(featureName.trim());
            String featureValue = environment.getProperty(featureKey);
            if (StringUtils.hasText(featureValue)) {
                return normalize(StudioPersistenceType.parse(featureValue, featureKey), featureKey);
            }
        }

        String globalValue = environment.getProperty(PropertyKeys.Persistence.TYPE);
        if (StringUtils.hasText(globalValue)) {
            return normalize(StudioPersistenceType.parse(globalValue, PropertyKeys.Persistence.TYPE),
                    PropertyKeys.Persistence.TYPE);
        }

        StudioPersistenceType fallback = defaultType == null ? StudioPersistenceType.JPA : defaultType;
        return fallback.normalized();
    }

    private StudioPersistenceType normalize(StudioPersistenceType type, String propertyName) {
        if (type == StudioPersistenceType.JDBC) {
            log.warn("[DEPRECATED CONFIG] {}=jdbc is deprecated. Use mybatis instead.", propertyName);
        }
        return type.normalized();
    }

    private String featurePropertyKey(String featureName) {
        return PropertyKeys.Features.PREFIX + "." + featureName + ".persistence";
    }
}
