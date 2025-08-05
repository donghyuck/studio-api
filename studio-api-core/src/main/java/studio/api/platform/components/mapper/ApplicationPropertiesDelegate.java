package studio.api.platform.components.mapper;

import java.util.List;
import java.util.Map;

public interface ApplicationPropertiesDelegate {
    
    public static final String SERVICE_NAME = "components:application-properties-mapper";

    List<Map<String, String>> selectAll();
    void insertProperty(String name, String value);
    void updateProperty(String name, String value);
    void deleteProperty(String name);

}
