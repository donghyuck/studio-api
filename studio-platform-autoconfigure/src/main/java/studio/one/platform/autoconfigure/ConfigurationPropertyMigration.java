package studio.one.platform.autoconfigure;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Small helper for temporary configuration namespace migrations.
 */
public final class ConfigurationPropertyMigration {

    private static final Set<String> WARNED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private ConfigurationPropertyMigration() {
    }

    public static boolean hasProperties(Environment environment, String prefix) {
        return Binder.get(environment)
                .bind(prefix, Bindable.mapOf(String.class, Object.class))
                .isBound();
    }

    public static <T> T bindLegacyFallbackIfTargetMissing(
            Environment environment,
            String targetPrefix,
            String legacyPrefix,
            T target,
            Logger log,
            String reason) {
        if (target == null || hasProperties(environment, targetPrefix) || !hasProperties(environment, legacyPrefix)) {
            return target;
        }
        Binder.get(environment).bind(legacyPrefix, Bindable.ofInstance(target));
        warnDeprecated(log, legacyPrefix + ".*", targetPrefix + ".*", reason);
        return target;
    }

    public static boolean getBooleanWithLegacyFallback(
            Environment environment,
            String targetKey,
            String legacyKey,
            boolean defaultValue,
            Logger log,
            String reason) {
        String targetValue = environment.getProperty(targetKey);
        if (StringUtils.hasText(targetValue)) {
            return Boolean.parseBoolean(targetValue);
        }
        String legacyValue = environment.getProperty(legacyKey);
        if (StringUtils.hasText(legacyValue)) {
            warnDeprecated(log, legacyKey, targetKey, reason);
            return Boolean.parseBoolean(legacyValue);
        }
        return defaultValue;
    }

    public static <T> void bindLegacyLeafIfTargetMissing(
            Environment environment,
            String targetPrefix,
            String legacyPrefix,
            String propertyName,
            Class<T> type,
            Consumer<T> setter,
            Logger log,
            String reason) {
        String targetKey = targetPrefix + "." + propertyName;
        if (environment.containsProperty(targetKey)) {
            return;
        }
        String legacyKey = legacyPrefix + "." + propertyName;
        Binder.get(environment).bind(legacyKey, Bindable.of(type)).ifBound(value -> {
            setter.accept(value);
            warnDeprecated(log, legacyKey, targetKey, reason);
        });
    }

    public static void warnDeprecated(Logger log, String legacyKey, String replacementKey, String reason) {
        String warningKey = legacyKey + "->" + replacementKey;
        if (!WARNED.add(warningKey)) {
            return;
        }
        log.warn("[DEPRECATED CONFIG] {} is deprecated. Use {} instead. {} "
                + "The legacy key will be removed after the configured migration window.",
                legacyKey, replacementKey, reason);
    }
}
