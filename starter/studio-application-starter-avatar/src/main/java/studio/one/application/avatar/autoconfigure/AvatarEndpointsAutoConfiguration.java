package studio.one.application.avatar.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.user.domain.model.User;
import studio.echo.base.user.service.ApplicationUserService;
import studio.echo.platform.autoconfigure.i18n.I18nKeys;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.service.I18n;
import studio.echo.platform.util.LogUtils;
import studio.one.application.avatar.service.AvatarImageService;
import studio.one.application.web.controller.AvatarController;
import studio.one.application.web.controller.PublicAvatarController;

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