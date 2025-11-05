package studio.one.platform.security.autoconfigure;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.validation.constraints.Pattern;

import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
public class WebProperties {
    
    private boolean enabled = true;

    @Pattern(regexp = "^/.*$", message = "basePath must start with '/'")
    private String basePath ;

    private Set<String> allowAuthorities = new LinkedHashSet<>();

    private Set<String> allowIpCidr = new LinkedHashSet<>();
    
    private Set<String> allowUsernames = new LinkedHashSet<>();

}
