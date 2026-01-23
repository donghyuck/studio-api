package studio.one.platform.autoconfigure.objecttype;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "studio.objecttype")
@Getter
@Setter
@Validated
public class ObjectTypeProperties {

    public enum Mode {
        yaml,
        db
    }

    private Mode mode = Mode.yaml;

    private Yaml yaml = new Yaml();

    private Db db = new Db();

    private Registry registry = new Registry();

    private Policy policy = new Policy();

    @Getter
    @Setter
    public static class Yaml {
        private String resource = "classpath:objecttype.yml";
    }

    @Getter
    @Setter
    public static class Db {
        private boolean enabled = false;
    }

    @Getter
    @Setter
    public static class Registry {
        private Cache cache = new Cache();
    }

    @Getter
    @Setter
    public static class Policy {
        private Cache cache = new Cache();
    }

    @Getter
    @Setter
    public static class Cache {
        private boolean enabled = true;
        private long ttlSeconds = 300;
        private long maxSize = 1000;
    }
}
