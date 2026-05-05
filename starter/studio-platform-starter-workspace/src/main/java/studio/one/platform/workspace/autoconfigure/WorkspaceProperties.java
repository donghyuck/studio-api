package studio.one.platform.workspace.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "studio.workspace")
@Getter
@Setter
@Validated
public class WorkspaceProperties {

    private Tree tree = new Tree();
    private Slug slug = new Slug();
    private Permission permission = new Permission();

    @Getter
    @Setter
    public static class Tree {
        private int maxDepth = 10;
        private int maxChildrenPerNode = 200;
    }

    @Getter
    @Setter
    public static class Slug {
        private int maxLength = 100;
    }

    @Getter
    @Setter
    public static class Permission {
        private boolean inheritParentRole = true;
        private boolean denyOverrideEnabled = false;
    }
}
