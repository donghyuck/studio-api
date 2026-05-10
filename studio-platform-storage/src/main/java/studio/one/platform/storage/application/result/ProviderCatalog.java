package studio.one.platform.storage.application.result;

import java.util.List;

public interface ProviderCatalog {

    public static final String SERVICE_NAME = "services:cloud:objectstorage:catalog";
    
    List<ProviderInfo> list(boolean includeHealth);

}
