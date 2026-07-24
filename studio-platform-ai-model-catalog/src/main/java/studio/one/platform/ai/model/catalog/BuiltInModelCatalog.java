package studio.one.platform.ai.model.catalog;

public final class BuiltInModelCatalog {

    public static final String RESOURCE = "/studio/one/platform/ai/model/catalog/models-2026.07.23.json";

    private BuiltInModelCatalog() {
    }

    public static DefaultModelCatalog load() {
        return new CatalogResourceLoader().load(BuiltInModelCatalog.class.getResourceAsStream(RESOURCE));
    }
}
