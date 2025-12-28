package studio.one.platform.security.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogoutProperties {
    private boolean enabled = false;
    private String logoutUrl = "/logout";
    private String logoutSuccessUrl = "/login?logout";
    private boolean invalidateSession = true;
    private List<String> deleteCookies = new ArrayList<>();
}
