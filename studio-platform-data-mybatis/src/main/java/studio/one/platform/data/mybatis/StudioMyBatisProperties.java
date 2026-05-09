package studio.one.platform.data.mybatis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = StudioMyBatisConventions.PROPERTIES_PREFIX)
public class StudioMyBatisProperties {

    private List<String> mapperLocations = new ArrayList<>(StudioMyBatisConventions.defaultMapperLocations());
    private String typeAliasesPackage;
    private String typeHandlersPackage;
    private boolean mapUnderscoreToCamelCase = true;
    private Map<String, String> databaseIdAliases = defaultDatabaseIdAliases();

    public List<String> getMapperLocations() {
        return mapperLocations;
    }

    public void setMapperLocations(List<String> mapperLocations) {
        this.mapperLocations = mapperLocations;
    }

    public String getTypeAliasesPackage() {
        return typeAliasesPackage;
    }

    public void setTypeAliasesPackage(String typeAliasesPackage) {
        this.typeAliasesPackage = typeAliasesPackage;
    }

    public String getTypeHandlersPackage() {
        return typeHandlersPackage;
    }

    public void setTypeHandlersPackage(String typeHandlersPackage) {
        this.typeHandlersPackage = typeHandlersPackage;
    }

    public boolean isMapUnderscoreToCamelCase() {
        return mapUnderscoreToCamelCase;
    }

    public void setMapUnderscoreToCamelCase(boolean mapUnderscoreToCamelCase) {
        this.mapUnderscoreToCamelCase = mapUnderscoreToCamelCase;
    }

    public Map<String, String> getDatabaseIdAliases() {
        return databaseIdAliases;
    }

    public void setDatabaseIdAliases(Map<String, String> databaseIdAliases) {
        this.databaseIdAliases = databaseIdAliases;
    }

    private static Map<String, String> defaultDatabaseIdAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("PostgreSQL", "postgresql");
        aliases.put("MySQL", "mysql");
        aliases.put("MariaDB", "mariadb");
        aliases.put("H2", "h2");
        aliases.put("Oracle", "oracle");
        aliases.put("Microsoft SQL Server", "sqlserver");
        return aliases;
    }
}
