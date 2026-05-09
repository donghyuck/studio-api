package studio.one.platform.ai.adapters.vector.mybatis;

import java.util.List;

public final class PgVectorMetadataInCriterion {

    private final String key;
    private final List<String> values;

    public PgVectorMetadataInCriterion(String key, List<String> values) {
        this.key = key;
        this.values = values == null ? List.of() : List.copyOf(values);
    }

    public String getKey() {
        return key;
    }

    public List<String> getValues() {
        return values;
    }
}
