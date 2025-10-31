package studio.one.application.avatar.autoconfigure;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.user.domain.model.Role;
import studio.echo.base.user.domain.model.User;
import studio.echo.base.user.service.ApplicationUserService;
import studio.echo.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.echo.platform.autoconfigure.i18n.I18nKeys;
import studio.echo.platform.component.State;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.service.I18n;
import studio.echo.platform.service.Repository;
import studio.echo.platform.util.I18nUtils;
import studio.echo.platform.util.LogUtils;
import studio.one.application.avatar.domain.entity.AvatarImage;
import studio.one.application.avatar.domain.repository.AvatarImageDataRepository;
import studio.one.application.avatar.domain.repository.AvatarImageRepository;
import studio.one.application.avatar.replica.FileReplicaStore;
import studio.one.application.avatar.service.AvatarImageService;
import studio.one.application.avatar.service.impl.AvatarImageFilesystemReplicaService;
import studio.one.application.avatar.service.impl.AvatarImageServiceImpl;

@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties({ AvatarFeatureProperties.class }) 
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".avatar-image", name = "enabled", havingValue = "true")
@Slf4j
public class AvatarAutoConfiguration {

    private static final String FEATURE_NAME = "Avatar"; 

    @Bean(name = AvatarImageService.SERVICE_NAME )
    @ConditionalOnMissingBean(name = AvatarImageService.SERVICE_NAME )
    public AvatarImageService<User> avatarImageService( 
            AvatarImageRepository avatarImageRepository,
            AvatarImageDataRepository avatarImageDataRepository, 
            ApplicationUserService<User, Role> userService,
            ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
        LogUtils.blue(AvatarImageServiceImpl.class, true), LogUtils.red(State.CREATED.toString())));
        return new AvatarImageServiceImpl(avatarImageRepository, avatarImageDataRepository, userService);
    }

    @Bean(name = FileReplicaStore.SERVICE_NAME )
    @ConditionalOnMissingBean(name = FileReplicaStore.SERVICE_NAME )
    public FileReplicaStore replicaStore( 
            AvatarFeatureProperties properties,
            Repository repository,
            ObjectProvider<I18n> i18nProvider) throws IOException {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
        LogUtils.blue(FileReplicaStore.class, true), LogUtils.red(State.CREATED.toString())));
        File baseDir;
        String baseDirPath = properties.getReplica().getBaseDir();
        if( baseDirPath == null || baseDirPath.isBlank() ) {
            File dir = repository.getFile(FileReplicaStore.DEFAULT_IMAGE_FILE_DIR );
            baseDir = new File( dir, "avatars");
        }else {
            baseDir = new File(baseDirPath);
        }
        if(properties.getReplica().isEnsureDirs()){
            if(!baseDir.exists()) {
                baseDir.mkdirs();
                log.debug("Created avatar replica base directory: {}", 
                LogUtils.blue(baseDir.getAbsolutePath()));
            }   
        } 
        return new FileReplicaStore(Paths.get(baseDir.toURI()));
    }
    
    @Primary
    @Bean
    @ConditionalOnMissingBean
    public AvatarImageService<User> avatarImageFilesystemReplicaService(
            @Qualifier( AvatarImageService.SERVICE_NAME) AvatarImageService<User> delegate,
            FileReplicaStore replicas,
            ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
        LogUtils.blue(AvatarImageFilesystemReplicaService.class, true), LogUtils.red(State.CREATED.toString())));
        return new AvatarImageFilesystemReplicaService(delegate, replicas );
    }

    // @Bean
    // @ConditionalOnProperty( prefix = PropertyKeys.Features.PREFIX + ".avatar-image.replica.cleanup", name = "enabled", havingValue = "true")
    // public TaskScheduler avatarCleanupScheduler(FileReplicaStore store) {
    //     ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    //     scheduler.setPoolSize(1);
    //     scheduler.setThreadNamePrefix("avatar-cleanup-");
    //     scheduler.initialize();
    //     return scheduler;
    // }

    // @Bean
    // @ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".avatar-image.replica.cleanup", name = "enabled", havingValue = "true")
    // public SchedulingConfigurer avatarCleanupJob(AvatarFeatureProperties props, FileReplicaStore store) {
    //     return taskRegistrar -> taskRegistrar.addTriggerTask( 
    //         () -> {/* walk baseDir & delete old files per ttlDays */},
    //         ctx -> {
    //             String cron = props.getReplica().getCleanup().getCron();
    //             return new CronTrigger(cron).nextExecutionTime(ctx);
    //         }
    //     );
    // }

    @Configuration
    @AutoConfigureBefore(HibernateJpaAutoConfiguration.class) 
    @SuppressWarnings("java:S1118")
    static class EntityScanConfig {
        @Bean
        static BeanDefinitionRegistryPostProcessor  entityScanRegistrar(Environment env , ObjectProvider<I18n> i18nProvider) { 
            I18n i18n = I18nUtils.resolve(i18nProvider);
            String entityKey = PropertyKeys.Features.PREFIX + ".avatar" + ".entity-packages";
            String packageName = AvatarImage.class.getPackageName();
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EntityScan.PREPARING, FEATURE_NAME, entityKey, packageName  ));
            return EntityScanRegistrarSupport.entityScanRegistrar(
                entityKey,  packageName
            );
        }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureAfter(EntityScanConfig.class)
    @ConditionalOnBean(EntityManagerFactory.class)
    @EnableJpaRepositories(basePackages =  "studio.one.application.avatar.domain.repository") 
    static class JpaWiring {
    }
}
