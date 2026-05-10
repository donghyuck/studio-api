package studio.one.platform.storage.application.result;

import java.util.List;

import studio.one.platform.storage.web.dto.response.ProviderInfoDto;

public interface ProviderCatalog {

    public static final String SERVICE_NAME = "services:cloud:objectstorage:catalog";
    
    List<ProviderInfoDto> list(boolean includeHealth);

}
