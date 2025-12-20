package studio.one.platform.realtime.autoconfigure;

import java.util.Set;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

public class RedisAutoConfigurationFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    private static final Set<String> REDIS_AUTO_CONFIGS = Set.of(
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean[] match = new boolean[autoConfigurationClasses.length];
        boolean excludeRedis = shouldExcludeRedisAutoConfiguration();
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            String autoConfigurationClass = autoConfigurationClasses[i];
            match[i] = !excludeRedis || !REDIS_AUTO_CONFIGS.contains(autoConfigurationClass);
        }
        return match;
    }

    private boolean shouldExcludeRedisAutoConfiguration() {
        if (environment == null) {
            return false;
        }
        String key = "studio.realtime.stomp.redis-enabled";
        if (!environment.containsProperty(key)) {
            return false;
        }
        return !environment.getProperty(key, Boolean.class, false);
    }
}
