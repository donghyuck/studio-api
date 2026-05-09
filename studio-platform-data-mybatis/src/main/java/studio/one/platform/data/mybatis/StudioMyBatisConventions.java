package studio.one.platform.data.mybatis;

import java.util.List;

public final class StudioMyBatisConventions {

    public static final String PROPERTIES_PREFIX = "studio.mybatis";
    public static final String DEFAULT_MAPPER_LOCATION = "classpath*:mybatis/**/*.xml";

    private StudioMyBatisConventions() {
    }

    public static List<String> defaultMapperLocations() {
        return List.of(DEFAULT_MAPPER_LOCATION);
    }
}
