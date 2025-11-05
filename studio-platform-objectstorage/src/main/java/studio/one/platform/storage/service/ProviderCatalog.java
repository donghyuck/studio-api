package studio.one.platform.storage.service;

import java.util.List;

import studio.one.platform.storage.web.dto.ProviderInfo;

public interface ProviderCatalog {

    public static final String SERVICE_NAME = "services:cloud:objectstorage:catalog";
    
    List<ProviderInfo> list(boolean includeHealth);

}
