package studio.one.platform.user.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "studio.bootstrap.user")
public class UserBootstrapProperties {

    private boolean enabled = false;

    private List<RoleDefinition> roles = defaultRoles();

    private Admin admin = new Admin();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<RoleDefinition> getRoles() {
        return roles;
    }

    public void setRoles(List<RoleDefinition> roles) {
        this.roles = roles;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    private static List<RoleDefinition> defaultRoles() {
        List<RoleDefinition> defaults = new ArrayList<>();
        defaults.add(new RoleDefinition("ROLE_ADMIN", "Platform administrator"));
        defaults.add(new RoleDefinition("ROLE_USER", "Default user"));
        return defaults;
    }

    public static class RoleDefinition {
        private String name;
        private String description;

        public RoleDefinition() {
        }

        public RoleDefinition(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class Admin {
        private boolean enabled = false;
        private String username = "local-admin";
        private String email = "local-admin@example.local";
        private String name = "Local Admin";
        private String password;
        private List<String> roles = List.of("ROLE_ADMIN");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }
}
