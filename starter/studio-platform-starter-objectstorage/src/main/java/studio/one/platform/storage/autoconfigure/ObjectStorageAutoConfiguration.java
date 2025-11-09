/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file ObjectStorageAutoConfiguration.java
 *      @date 2025
 *
 */

package studio.one.platform.storage.autoconfigure;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import studio.one.platform.autoconfigure.i18n.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.storage.service.CloudObjectStorage;
import studio.one.platform.storage.service.ObjectStorageRegistry;
import studio.one.platform.storage.service.impl.S3ObjectStorage;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

/**
 *
 * @author donghyuck, son
 * @since 2025-11-03
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-03  donghyuck, son: 최초 생성.
 *          </pre>
 */

@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
@Slf4j
public class ObjectStorageAutoConfiguration {

    protected static final String FEATURE_NAME = "ObjectStorage";

    static Map<String, StorageProperties.Provider> materializeProviders(StorageProperties props) {
        return new LinkedHashMap<>(props.getProviders());
    }
 
    @Configuration
    @ConditionalOnClass(S3Client.class)
    static class S3Available {

        @Bean
        public List<CloudObjectStorage> s3Providers(StorageProperties props, I18n i18n) {
            List<CloudObjectStorage> result = new ArrayList<>();
            var providers = materializeProviders(props);
            providers.forEach((id, p) -> {
                if (!p.isEnabled() || !"s3".equalsIgnoreCase(p.getType()))
                    return;
                var client = createS3Client(id, p);
                String endpoint = p.getEndpoint();
                String region = p.getRegion();
                var presigner = Boolean.TRUE.equals(p.getS3().getPresignerEnabled()) ? createPresigner(p) : null;
                result.add(new S3ObjectStorage(id, client, region, endpoint, presigner));
                log.info(LogUtils.format(I18nUtils.safe(i18n), I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                        FEATURE_NAME,
                        LogUtils.blue(S3ObjectStorage.class, true),
                        LogUtils.green(id),
                        LogUtils.red(State.CREATED.toString())));

            });
            return result;
        }

        @Bean(name = ObjectStorageRegistry.SERVICE_NAME )
        @ConditionalOnMissingBean( name = ObjectStorageRegistry.SERVICE_NAME)
        public ObjectStorageRegistry objectStorageRegistry(List<CloudObjectStorage> providers, I18n i18n){ 
            var registory = new ObjectStorageRegistry(providers);
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(ObjectStorageRegistry.class, true), LogUtils.red(State.CREATED.toString())));
                return registory;
        }

        private static S3Client createS3Client(String id, StorageProperties.Provider p) {
            String endpoint = p.getEndpoint();
            AwsCredentialsProvider creds = createCredentialsProvider(p.getCredentials());
            var s3cfg = S3Configuration.builder()
                    .pathStyleAccessEnabled(Boolean.TRUE.equals(p.getS3().getPathStyle()))
                    .build();
            var b = S3Client.builder()
                    .credentialsProvider(creds)
                    .serviceConfiguration(s3cfg)
                    .region(Region.of(Optional.ofNullable(p.getRegion()).orElse("us-east-1")));
            if (StringUtils.hasText(endpoint))
                b.endpointOverride(URI.create(endpoint));

            var override = ClientOverrideConfiguration.builder();
            if (p.getS3().getApiTimeoutMs() != null)
                override.apiCallTimeout(Duration.ofMillis(p.getS3().getApiTimeoutMs()));
            b.overrideConfiguration(override.build());
            return b.build();
        }

        private static S3Presigner createPresigner(StorageProperties.Provider p) {
            var presigner = S3Presigner.builder()
                    .region(Region.of(Optional.ofNullable(p.getRegion()).orElse("us-east-1")));
            String endpoint = p.getEndpoint();
            if (StringUtils.hasText(endpoint))
                presigner.endpointOverride(URI.create(endpoint));

            presigner.credentialsProvider(createCredentialsProvider(p.getCredentials()));
            return presigner.build();
        }

        private static AwsCredentialsProvider createCredentialsProvider(StorageProperties.Credentials c) {
            var ak = c.getAccessKey();
            var sk = c.getSecretKey();
            var st = c.getSessionToken();
            if (!StringUtils.hasText(ak) || !StringUtils.hasText(sk))
                return DefaultCredentialsProvider.builder().build();
            if (StringUtils.hasText(st))
                return StaticCredentialsProvider.create(AwsSessionCredentials.create(ak, sk, st));
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(ak, sk));
        }
    }

    // S3 라이브러리 없는데 s3 provider가 활성화된 경우 경고
    @Configuration
    @ConditionalOnMissingClass("software.amazon.awssdk.services.s3.S3Client")
    static class S3Missing {
        @Bean
        public BeanFactoryPostProcessor s3MissingWarning(StorageProperties props, I18n i18n) {
            return bf -> {
                var providers = materializeProviders(props).values();
                boolean anyS3 = providers.stream().anyMatch(p -> p.isEnabled() && "s3".equalsIgnoreCase(p.getType()));
                if (anyS3) {
                    log.warn(
                            i18n.get("warn.autoconfig.feature.missing-lib", "S3 Provider",
                                    "cloud.storage.profiders.<id>.enabled=true", "software.amazon.awssdk:s3"));
                    // Fail-Fast:
                    // throw new BeanCreationException("Missing 'software.amazon.awssdk:s3'
                }
            };
        }
    }

}
