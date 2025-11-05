package studio.one.platform.storage.autoconfigure;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


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

import studio.one.platform.storage.service.ObjectStorageRegistry; 
import studio.one.platform.storage.web.controller.ObjectStorageController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AutoConfiguration
@AutoConfigureAfter(ObjectStorageAutoConfiguration.class)
@EnableConfigurationProperties(StorageProperties.class)
@Slf4j
public class ObjectStorageEndpointAutoConfiguration {

    @Bean 
    @ConditionalOnProperty(prefix = PropertyKeys.Cloud.PREFIX + ".storage.web", name = "enabled", havingValue = "true")
    public ObjectStorageController endpointObjectStorage(
        StorageProperties props,
        ObjectStorageRegistry objectStorageRegistry,
        ProviderCatalog catalog,
        I18n i18n
        ) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, ObjectStorageAutoConfiguration.FEATURE_NAME,
                LogUtils.blue(ObjectStorageRegistry.class, true),
                LogUtils.blue(ObjectStorageController.class, true),
                props.getWeb().getEndpoint(), "-"));
        return new ObjectStorageController(objectStorageRegistry, catalog, i18n);
    }
}