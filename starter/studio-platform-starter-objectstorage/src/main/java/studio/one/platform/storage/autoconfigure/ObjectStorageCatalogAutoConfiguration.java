package studio.one.platform.storage.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;


import studio.one.platform.exception.ConfigurationError;
import studio.one.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.one.platform.autoconfigure.i18n.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.service.I18n;
import studio.one.platform.service.Repository;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;
import studio.one.platform.component.State;

import studio.one.platform.storage.autoconfigure.StorageProperties;
import studio.one.platform.storage.service.CloudObjectStorage;
import studio.one.platform.storage.service.ProviderCatalog; 
import studio.one.platform.storage.service.impl.ProviderCatalogImpl; 
import studio.one.platform.storage.service.ObjectStorageRegistry; 
import studio.one.platform.storage.web.controller.ObjectStorageController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
