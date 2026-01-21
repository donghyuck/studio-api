package studio.one.application.avatar.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.avatar.service.AvatarImageService;
import studio.one.application.web.controller.AvatarController;
import studio.one.application.web.controller.MeAvatarController;
import studio.one.application.web.controller.PublicAvatarController;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;
import studio.one.platform.util.LogUtils;

@Configuration
@AutoConfigureAfter(AvatarAutoConfiguration.class)
@RequiredArgsConstructor
@EnableConfigurationProperties({ AvatarFeatureProperties.class })
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".avatar-image", name = "enabled", havingValue = "true")
@Slf4j
class AvatarEndpointsAutoConfiguration {
 
    private final AvatarFeatureProperties props;
    private final I18n i18n;

    @Bean
    @ConditionalOnMissingBean(AvatarController.class)
    AvatarController avatarController(AvatarImageService avatarImageService) {

        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, AvatarAutoConfiguration.FEATURE_NAME,
                LogUtils.blue(avatarImageService.getClass(), true),
                LogUtils.blue(AvatarController.class, true),
                props.getWeb().getMgmtBase(),
                "CRUD"));

        return new AvatarController(avatarImageService);
    }

    @Bean
    @ConditionalOnMissingBean(PublicAvatarController.class)
    PublicAvatarController publicAvatarController(AvatarImageService avatarImageService) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, AvatarAutoConfiguration.FEATURE_NAME,
                LogUtils.blue(avatarImageService.getClass(), true),
                LogUtils.blue(PublicAvatarController.class, true),
                props.getWeb().getPublicBase(),
                "R"));
                
        return new PublicAvatarController(avatarImageService);
    }

    @Bean
    @ConditionalOnMissingBean(MeAvatarController.class)
    MeAvatarController meAvatarController(AvatarImageService avatarImageService) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, AvatarAutoConfiguration.FEATURE_NAME,
                LogUtils.blue(avatarImageService.getClass(), true),
                LogUtils.blue(MeAvatarController.class, true),
                props.getWeb().getPublicBase(),
                "CRUD"));
        return new MeAvatarController(avatarImageService);
    }

}
