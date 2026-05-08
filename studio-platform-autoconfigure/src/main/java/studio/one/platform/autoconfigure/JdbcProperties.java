package studio.one.platform.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Persistence.Jdbc.PREFIX)
@Getter
@Setter
public class JdbcProperties {

    private boolean enabled = true;

}
