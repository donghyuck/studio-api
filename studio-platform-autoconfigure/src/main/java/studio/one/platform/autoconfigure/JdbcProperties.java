package studio.one.platform.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Persistence.Jdbc.PREFIX)
@Getter
@Setter
public class JdbcProperties {

    private boolean enabled = true;
    private SqlProps sql = new SqlProps();
    
    @Getter
    @Setter
    public static class SqlProps {
        private boolean enabled = true;
        private boolean failFast = true;
        private List<String> locations = new ArrayList<>();
        private DeployProperties deploy = new DeployProperties();
    }

    @Getter
    @Setter
    public static class DeployProperties {
        private Auto auto = new Auto();
    }

    @Getter
    @Setter
    public static class Auto {
        private List<String> locations = new ArrayList<>();
        private boolean watch = false;
        private Duration interval = Duration.ofSeconds(30);
    }

}
