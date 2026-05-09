package studio.one.platform.mybatis.autoconfigure.fixture;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExtraMapper {

    int selectExtra();
}
