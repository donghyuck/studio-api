package studio.one.platform.ai.model;

import java.util.List;
import java.util.Optional;

public interface ModelCatalog {

    Optional<ModelDefinition> find(String idOrAlias);

    List<ModelDefinition> definitions();
}
