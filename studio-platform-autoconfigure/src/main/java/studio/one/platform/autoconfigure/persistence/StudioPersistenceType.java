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
        switch (this) {
            case JPA:
                return PersistenceProperties.Type.jpa;
            case MYBATIS:
                return PersistenceProperties.Type.mybatis;
            case JDBC:
                return PersistenceProperties.Type.jdbc;
            default:
                throw new IllegalStateException("Unsupported persistence type: " + this);
        }
    }

    public static StudioPersistenceType from(PersistenceProperties.Type type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case jpa:
                return JPA;
            case mybatis:
                return MYBATIS;
            case jdbc:
                return JDBC;
            default:
                throw new IllegalArgumentException("Unsupported persistence type: " + type);
        }
    }

    public static StudioPersistenceType parse(String raw, String propertyName) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Persistence type must not be empty: " + propertyName);
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "jpa":
                return JPA;
            case "mybatis":
                return MYBATIS;
            case "jdbc":
                return JDBC;
            default:
                throw new IllegalArgumentException("Unsupported persistence type '" + raw + "' for "
                    + propertyName + ". Supported values are: jpa, mybatis, jdbc(deprecated alias).");
        }
    }
}
