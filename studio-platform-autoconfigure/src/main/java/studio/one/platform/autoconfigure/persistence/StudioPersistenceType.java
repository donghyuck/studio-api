package studio.one.platform.autoconfigure.persistence;

import java.util.Locale;

import studio.one.platform.autoconfigure.PersistenceProperties;

public enum StudioPersistenceType {
    JPA,
    MYBATIS,

    /**
     * @deprecated Use {@link #MYBATIS}. Kept as a 2.x compatibility alias.
     */
    @Deprecated(forRemoval = false)
    JDBC;

    public StudioPersistenceType normalized() {
        return this == JDBC ? MYBATIS : this;
    }

    public boolean isJpa() {
        return normalized() == JPA;
    }

    public boolean isMyBatis() {
        return normalized() == MYBATIS;
    }

    public PersistenceProperties.Type toLegacyType() {
        return switch (this) {
            case JPA -> PersistenceProperties.Type.jpa;
            case MYBATIS -> PersistenceProperties.Type.mybatis;
            case JDBC -> PersistenceProperties.Type.jdbc;
        };
    }

    public static StudioPersistenceType from(PersistenceProperties.Type type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case jpa -> JPA;
            case mybatis -> MYBATIS;
            case jdbc -> JDBC;
        };
    }

    public static StudioPersistenceType parse(String raw, String propertyName) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Persistence type must not be empty: " + propertyName);
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "jpa" -> JPA;
            case "mybatis" -> MYBATIS;
            case "jdbc" -> JDBC;
            default -> throw new IllegalArgumentException("Unsupported persistence type '" + raw + "' for "
                    + propertyName + ". Supported values are: jpa, mybatis, jdbc(deprecated alias).");
        };
    }
}
