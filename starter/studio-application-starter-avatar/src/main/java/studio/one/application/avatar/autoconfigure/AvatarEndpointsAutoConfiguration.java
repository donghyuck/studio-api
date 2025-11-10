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
import studio.one.application.web.controller.PublicAvatarController;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.service.ApplicationUserService;
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

    private static final String FEATURE_NAME = "Avatar";
    private final AvatarFeatureProperties props;
    private final I18n i18n;

    @Bean
    @ConditionalOnMissingBean(AvatarController.class)
    AvatarController avatarController(AvatarImageService<User> avatarImageService) {

        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, FEATURE_NAME,
                LogUtils.blue(avatarImageService.getClass(), true),
                LogUtils.blue(AvatarController.class, true),
                props.getWeb().getUserBase(),
                "CRUD"));

        return new AvatarController(avatarImageService);
    }

    @Bean
    @ConditionalOnMissingBean(PublicAvatarController.class)
    PublicAvatarController publicAvatarController(AvatarImageService<User> avatarImageService) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, FEATURE_NAME,
                LogUtils.blue(avatarImageService.getClass(), true),
                LogUtils.blue(PublicAvatarController.class, true),
                props.getWeb().getPublicBase(),
                "R"));
        return new PublicAvatarController(avatarImageService);
    }

}