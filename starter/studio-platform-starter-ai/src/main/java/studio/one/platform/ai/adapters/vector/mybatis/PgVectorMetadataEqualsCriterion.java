package studio.one.platform.ai.adapters.vector.mybatis;

public final class PgVectorMetadataEqualsCriterion {

    private final String key;
    private final String value;

    public PgVectorMetadataEqualsCriterion(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
