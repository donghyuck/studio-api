package studio.one.platform.autoconfigure.persistence;

/**
 * Resolves normalized persistence choices for new or migrated MyBatis-aware
 * code paths. Existing legacy JDBC-backed auto-configurations should continue
 * using their raw {@code PersistenceProperties.Type.jdbc} gates until that
 * feature has a MyBatis replacement.
 */
public interface PersistenceTypeResolver {

    StudioPersistenceType resolve(String featureName, StudioPersistenceType defaultType);

    default StudioPersistenceType resolve(String featureName) {
        return resolve(featureName, StudioPersistenceType.JPA);
    }
}
