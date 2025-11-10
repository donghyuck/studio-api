package studio.one.platform.user.autoconfigure;

import java.util.List;

import javax.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Features.User.PREFIX)
@Validated
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class UserFeatureProperties extends FeatureToggle {

    public static final String DEFAULT_REPOSITORY_PACKAGE = "studio.one.base.user.persistence.jpa";

    public static final String DEFAULT_ENTITY_PACKAGE = "studio.one.base.user.domain.entity";

    public static final String DEFAULT_COMPONENT_PACKAGE = "studio.one.base.user.service.impl";

    /** 멀티 EMF/Tx 환경 대비 기본 빈 이름 디폴트 */
    private String entityManagerFactory = "entityManagerFactory";

    private String transactionManager = "transactionManager";

    /** 콤마(,)로 여러 패키지 지정 가능 — 디폴트 제공 */
    @NotEmpty(message = "studio.features.user.repository-packages must not be empty when feature is enabled")
    private List<String> repositoryPackages = List.of(DEFAULT_REPOSITORY_PACKAGE);

    /** 선택: 엔티티 패키지 — 디폴트 제공 */
    @NotEmpty(message = "studio.features.user.entity-packages must not be empty when feature is enabled")
    private List<String> entityPackages = List.of(DEFAULT_ENTITY_PACKAGE);

    /** (선택) 컴포넌트 스캔 패키지들 */
    private List<String> componentPackages = List.of(DEFAULT_COMPONENT_PACKAGE);

 
}
