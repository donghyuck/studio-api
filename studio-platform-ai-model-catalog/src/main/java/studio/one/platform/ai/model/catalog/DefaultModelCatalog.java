package studio.one.platform.ai.model.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import studio.one.platform.ai.model.ModelCatalog;
import studio.one.platform.ai.model.ModelDefinition;

public final class DefaultModelCatalog implements ModelCatalog {

    private final List<ModelDefinition> definitions;
    private final Map<String, ModelDefinition> byIdOrAlias;

    public DefaultModelCatalog(List<ModelDefinition> definitions) {
        new ModelCatalogValidator().validate(definitions);
        this.definitions = List.copyOf(definitions);
        Map<String, ModelDefinition> index = new LinkedHashMap<>();
        for (ModelDefinition definition : definitions) {
            index.put(definition.catalogId(), definition);
            definition.aliases().forEach(alias -> index.put(alias, definition));
        }
        this.byIdOrAlias = Map.copyOf(index);
    }

    @Override
    public Optional<ModelDefinition> find(String idOrAlias) {
        if (idOrAlias == null || idOrAlias.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byIdOrAlias.get(idOrAlias.trim().toLowerCase(Locale.ROOT)));
    }

    @Override
    public List<ModelDefinition> definitions() {
        return definitions;
    }
}
