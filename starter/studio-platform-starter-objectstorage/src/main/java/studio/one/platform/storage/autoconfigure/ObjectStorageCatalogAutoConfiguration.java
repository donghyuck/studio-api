package studio.one.platform.storage.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.autoconfigure.i18n.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.storage.service.ObjectStorageRegistry;
import studio.one.platform.storage.service.ProviderCatalog;
import studio.one.platform.storage.service.impl.ProviderCatalogImpl;
import studio.one.platform.util.LogUtils;

@AutoConfiguration
@AutoConfigureAfter(ObjectStorageAutoConfiguration.class)
@EnableConfigurationProperties(StorageProperties.class)
@Slf4j
public class ObjectStorageCatalogAutoConfiguration {

    @Bean( name = ProviderCatalog.SERVICE_NAME)
    @ConditionalOnBean(ObjectStorageRegistry.class)
    public ProviderCatalog providerCatalog(ObjectStorageRegistry registry,  StorageProperties props, I18n i18n) {

        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, ObjectStorageAutoConfiguration.FEATURE_NAME,
                LogUtils.blue(ProviderCatalog.class, true), LogUtils.red(State.CREATED.toString())));

        return new ProviderCatalogImpl(registry, props); 
    }


}
