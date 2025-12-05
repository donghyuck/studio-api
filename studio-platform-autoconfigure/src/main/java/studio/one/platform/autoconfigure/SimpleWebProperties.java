package studio.one.platform.autoconfigure;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SimpleWebProperties {
    
    private boolean enabled = false;

    private String basePath;
}
