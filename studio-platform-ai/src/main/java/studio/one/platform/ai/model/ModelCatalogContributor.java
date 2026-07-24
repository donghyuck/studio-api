package studio.one.platform.ai.model;

import java.util.Collection;

@FunctionalInterface
public interface ModelCatalogContributor {

    Collection<ModelDefinition> definitions();
}
