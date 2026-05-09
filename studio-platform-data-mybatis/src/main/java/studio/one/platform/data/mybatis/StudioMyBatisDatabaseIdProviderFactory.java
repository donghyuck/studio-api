package studio.one.platform.data.mybatis;

import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;

public final class StudioMyBatisDatabaseIdProviderFactory {

    private StudioMyBatisDatabaseIdProviderFactory() {
    }

    public static DatabaseIdProvider create(Map<String, String> aliases) {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties properties = new Properties();
        if (aliases != null) {
            aliases.forEach(properties::setProperty);
        }
        provider.setProperties(properties);
        return provider;
    }
}
