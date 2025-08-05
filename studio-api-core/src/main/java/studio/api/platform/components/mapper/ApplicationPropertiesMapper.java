package studio.api.platform.components.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.Mapper;

@Mapper(ApplicationPropertiesDelegate.SERVICE_NAME)
public interface ApplicationPropertiesMapper extends ApplicationPropertiesDelegate {

    List<Map<String, String>> selectAll();

    void insertProperty(@Param("name") String name, @Param("value") String value);

    void updateProperty(@Param("name") String name, @Param("value") String value);

    void deleteProperty(@Param("name") String name);

    void deletePropertyLike(@Param("name") String nameWithWildcard);

}