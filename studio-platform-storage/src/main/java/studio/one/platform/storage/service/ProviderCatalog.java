package studio.one.platform.storage.service;

import java.util.List;

import studio.one.platform.storage.web.dto.ProviderInfoDto;

public interface ProviderCatalog {

    public static final String SERVICE_NAME = "services:cloud:objectstorage:catalog";
    
    List<ProviderInfoDto> list(boolean includeHealth);

}
