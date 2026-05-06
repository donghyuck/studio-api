package studio.one.platform.workspace.autoconfigure;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import studio.one.base.user.service.ApplicationCompanyMemberService;
import studio.one.base.user.service.ApplicationCompanyService;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.workspace.permission.WorkspacePermissionContributor;
import studio.one.platform.workspace.persistence.jpa.WorkspaceClosureJpaRepository;
import studio.one.platform.workspace.persistence.jpa.WorkspaceEntity;
import studio.one.platform.workspace.persistence.jpa.WorkspaceJpaRepository;
import studio.one.platform.workspace.persistence.jpa.WorkspaceMemberJpaRepository;
import studio.one.platform.workspace.service.WorkspaceMemberService;
import studio.one.platform.workspace.service.WorkspacePermissionService;
import studio.one.platform.workspace.service.WorkspaceTreeService;
import studio.one.platform.workspace.service.impl.DefaultWorkspaceMemberService;
import studio.one.platform.workspace.service.impl.DefaultWorkspacePermissionService;
import studio.one.platform.workspace.service.impl.DefaultWorkspaceTreeService;
import studio.one.platform.workspace.service.impl.WorkspaceSettings;

@AutoConfiguration
@EnableConfigurationProperties({
        WorkspaceFeatureProperties.class,
        WorkspaceProperties.class })
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".workspace", name = "enabled", havingValue = "true")
public class WorkspaceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    WorkspaceSettings workspaceSettings(WorkspaceProperties properties, WorkspaceFeatureProperties featureProperties) {
        if (properties.getPermission().isDenyOverrideEnabled()) {
            throw new IllegalStateException("studio.workspace.permission.deny-override-enabled is reserved for a future release");
        }
        if (featureProperties.isCompanyScopeEnforced() && !featureProperties.isCompanyRequired()) {
            throw new IllegalStateException(
                    "studio.features.workspace.company-scope-enforced=true requires studio.features.workspace.company-required=true");
        }
        return new WorkspaceSettings(
                properties.getTree().getMaxDepth(),
                properties.getTree().getMaxChildrenPerNode(),
                properties.getSlug().getMaxLength(),
                properties.getPermission().isInheritParentRole(),
                featureProperties.isCompanyRequired(),
                featureProperties.isCompanyScopeEnforced());
    }

    @Bean
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".workspace", name = "persistence", havingValue = "jpa", matchIfMissing = true)
    WorkspaceCompanyScopeEnforcementGuard workspaceCompanyScopeEnforcementGuard(
            DataSource dataSource,
            EntityManagerFactory entityManagerFactory,
            WorkspaceFeatureProperties featureProperties) {
        WorkspaceCompanyScopeEnforcementGuard guard = new WorkspaceCompanyScopeEnforcementGuard();
        guard.verify(dataSource, featureProperties);
        return guard;
    }

    @Bean
    @ConditionalOnBean(WorkspaceJpaRepository.class)
    @ConditionalOnMissingBean
    WorkspacePermissionService workspacePermissionService(
            WorkspaceJpaRepository workspaceRepository,
            WorkspaceClosureJpaRepository closureRepository,
            WorkspaceMemberJpaRepository memberRepository,
            ObjectProvider<WorkspacePermissionContributor> contributors,
            ObjectProvider<ApplicationCompanyMemberService> companyMemberServiceProvider,
            WorkspaceProperties properties,
            WorkspaceSettings settings) {
        ApplicationCompanyMemberService companyMemberService = null;
        if (properties.getPermission().isCompanyOwnerOverrideEnabled()) {
            companyMemberService = companyMemberServiceProvider.getIfAvailable();
            if (companyMemberService == null) {
                throw new IllegalStateException(
                        "studio.workspace.permission.company-owner-override-enabled requires ApplicationCompanyMemberService");
            }
        }
        return new DefaultWorkspacePermissionService(
                workspaceRepository,
                closureRepository,
                memberRepository,
                contributors.orderedStream().toList(),
                settings,
                companyMemberService);
    }

    @Bean
    @ConditionalOnBean(WorkspaceJpaRepository.class)
    @ConditionalOnMissingBean
    WorkspaceTreeService workspaceTreeService(
            WorkspaceJpaRepository workspaceRepository,
            WorkspaceClosureJpaRepository closureRepository,
            WorkspaceMemberJpaRepository memberRepository,
            WorkspacePermissionService permissionService,
            WorkspaceSettings settings,
            ObjectProvider<ApplicationCompanyService> companyServiceProvider) {
        ApplicationCompanyService companyService = null;
        if (settings.companyRequired() || settings.companyScopeEnforced()) {
            companyService = companyServiceProvider.getIfAvailable();
            if (companyService == null) {
                throw new IllegalStateException(
                        "studio.features.workspace.company-required/company-scope-enforced requires ApplicationCompanyService");
            }
        } else {
            companyService = companyServiceProvider.getIfAvailable();
        }
        return new DefaultWorkspaceTreeService(
                workspaceRepository,
                closureRepository,
                memberRepository,
                permissionService,
                settings,
                companyService);
    }

    @Bean
    @ConditionalOnBean(WorkspaceJpaRepository.class)
    @ConditionalOnMissingBean
    WorkspaceMemberService workspaceMemberService(
            WorkspaceJpaRepository workspaceRepository,
            WorkspaceClosureJpaRepository closureRepository,
            WorkspaceMemberJpaRepository memberRepository,
            WorkspacePermissionService permissionService,
            EntityManager entityManager,
            ObjectProvider<ApplicationUserService<?, ?>> userServiceProvider) {
        return new DefaultWorkspaceMemberService(
                workspaceRepository,
                closureRepository,
                memberRepository,
                permissionService,
                entityManager,
                userServiceProvider.getIfAvailable());
    }

    @Configuration
    @AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
    @ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".workspace", name = "persistence", havingValue = "jpa", matchIfMissing = true)
    @SuppressWarnings("java:S1118")
    static class EntityScanConfig {
        @Bean
        static BeanDefinitionRegistryPostProcessor workspaceEntityScanRegistrar(Environment env) {
            String entityKey = PropertyKeys.Features.PREFIX + ".workspace.entity-packages";
            return EntityScanRegistrarSupport.entityScanRegistrar(entityKey, WorkspaceEntity.class.getPackageName());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureAfter(EntityScanConfig.class)
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".workspace", name = "persistence", havingValue = "jpa", matchIfMissing = true)
    @EnableJpaRepositories(basePackageClasses = {
            WorkspaceJpaRepository.class,
            WorkspaceClosureJpaRepository.class,
            WorkspaceMemberJpaRepository.class })
    static class WorkspaceJpaConfig {
    }

    static final class WorkspaceCompanyScopeEnforcementGuard {

        private static final String TABLE = "TB_PLATFORM_WORKSPACE";
        private static final String COMPANY_PATH_INDEX = "UK_PLATFORM_WORKSPACE_COMPANY_PATH";
        private static final String COMPANY_ROOT_SLUG_INDEX = "UK_PLATFORM_WORKSPACE_COMPANY_ROOT_SLUG";
        private static final String COMPANY_PARENT_SLUG_INDEX = "UK_PLATFORM_WORKSPACE_COMPANY_PARENT_SLUG";
        private static final String MYSQL = "mysql";
        private static final String MARIADB = "mariadb";

        void verify(DataSource dataSource, WorkspaceFeatureProperties featureProperties) {
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metadata = connection.getMetaData();
                boolean v1302Shape = hasV1302Shape(metadata, connection);
                if (featureProperties.isCompanyScopeEnforced() && !v1302Shape) {
                    throw new IllegalStateException(
                            "studio.features.workspace.company-scope-enforced=true requires V1302 workspace company scope schema");
                }
                if (!featureProperties.isCompanyScopeEnforced() && v1302Shape) {
                    throw new IllegalStateException(
                            "V1302 workspace company scope schema requires studio.features.workspace.company-scope-enforced=true");
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to verify workspace company scope enforcement schema", ex);
            }
        }

        private boolean hasV1302Shape(DatabaseMetaData metadata, Connection connection) throws SQLException {
            boolean baseShape = hasIndex(metadata, connection, COMPANY_PATH_INDEX)
                    && hasIndex(metadata, connection, COMPANY_PARENT_SLUG_INDEX)
                    && isColumnNotNull(metadata, connection, "COMPANY_ID");
            if (!baseShape) {
                return false;
            }
            String database = metadata.getDatabaseProductName().toLowerCase(Locale.ROOT);
            if (database.contains(MYSQL) || database.contains(MARIADB)) {
                return hasColumn(metadata, connection, "PARENT_KEY");
            }
            return hasIndex(metadata, connection, COMPANY_ROOT_SLUG_INDEX);
        }

        private boolean hasIndex(DatabaseMetaData metadata, Connection connection, String indexName) throws SQLException {
            return hasIndexInSchema(metadata, null, indexName)
                    || hasIndexInSchema(metadata, connection.getSchema(), indexName)
                    || hasIndexInSchema(metadata, null, indexName.toLowerCase(Locale.ROOT))
                    || hasIndexInSchema(metadata, connection.getSchema(), indexName.toLowerCase(Locale.ROOT));
        }

        private boolean hasIndexInSchema(DatabaseMetaData metadata, String schema, String indexName) throws SQLException {
            try (ResultSet indexes = metadata.getIndexInfo(null, schema, TABLE, true, false)) {
                while (indexes.next()) {
                    if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                        return true;
                    }
                }
            }
            try (ResultSet indexes = metadata.getIndexInfo(null, schema, TABLE.toLowerCase(Locale.ROOT), true, false)) {
                while (indexes.next()) {
                    if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean hasColumn(DatabaseMetaData metadata, Connection connection, String columnName) throws SQLException {
            return hasColumnInSchema(metadata, null, TABLE, columnName)
                    || hasColumnInSchema(metadata, connection.getSchema(), TABLE, columnName)
                    || hasColumnInSchema(metadata, null, TABLE.toLowerCase(Locale.ROOT), columnName)
                    || hasColumnInSchema(metadata, connection.getSchema(), TABLE.toLowerCase(Locale.ROOT), columnName);
        }

        private boolean hasColumnInSchema(
                DatabaseMetaData metadata,
                String schema,
                String tableName,
                String columnName) throws SQLException {
            try (ResultSet columns = metadata.getColumns(null, schema, tableName, columnName)) {
                if (columns.next()) {
                    return true;
                }
            }
            try (ResultSet columns = metadata.getColumns(
                    null,
                    schema,
                    tableName,
                    columnName.toLowerCase(Locale.ROOT))) {
                return columns.next();
            }
        }

        private boolean isColumnNotNull(DatabaseMetaData metadata, Connection connection, String columnName) throws SQLException {
            return isColumnNotNullInSchema(metadata, null, TABLE, columnName)
                    || isColumnNotNullInSchema(metadata, connection.getSchema(), TABLE, columnName)
                    || isColumnNotNullInSchema(metadata, null, TABLE.toLowerCase(Locale.ROOT), columnName)
                    || isColumnNotNullInSchema(metadata, connection.getSchema(), TABLE.toLowerCase(Locale.ROOT), columnName);
        }

        private boolean isColumnNotNullInSchema(
                DatabaseMetaData metadata,
                String schema,
                String tableName,
                String columnName) throws SQLException {
            try (ResultSet columns = metadata.getColumns(null, schema, tableName, columnName)) {
                while (columns.next()) {
                    return columns.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls;
                }
            }
            try (ResultSet columns = metadata.getColumns(
                    null,
                    schema,
                    tableName,
                    columnName.toLowerCase(Locale.ROOT))) {
                while (columns.next()) {
                    return columns.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls;
                }
            }
            return false;
        }
    }
}
