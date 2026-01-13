package studio.one.platform.autoconfigure;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter 
public class WebEndpointProperties {
    
    private boolean enabled = false;

    private String basePath;

    private String mgmtBasePath;

    private String version = "";

}
