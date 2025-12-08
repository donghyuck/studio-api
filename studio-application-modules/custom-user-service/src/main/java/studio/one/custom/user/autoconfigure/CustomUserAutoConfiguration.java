package studio.one.custom.user.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import studio.one.base.user.persistence.ApplicationUserRepository;
import studio.one.base.user.service.UserMutator;
import studio.one.custom.user.domain.entity.CustomUser;
import studio.one.custom.user.persistence.jpa.CustomUserJpaRepository;
import studio.one.custom.user.persistence.jdbc.CustomUserJdbcRepository;
import studio.one.custom.user.service.CustomUserMutator;
import studio.one.platform.constant.PropertyKeys;

/**
 * 커스텀 User 구현을 위한 자동 구성.
 * features.user.enabled=true 일 때 기본 User 구현이 없으면 CustomUser를 등록한다.
 */
@AutoConfiguration(before = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(CustomUser.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.User.PREFIX, name = "enabled", havingValue = "true")
@EntityScan(basePackageClasses = CustomUser.class)
@EnableJpaRepositories(basePackageClasses = CustomUserJpaRepository.class)
@ComponentScan(basePackageClasses = { CustomUserMutator.class, CustomUserJdbcRepository.class })
public class CustomUserAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(UserMutator.class)
    public UserMutator<CustomUser> customUserMutator() {
        return new CustomUserMutator();
    }
}
