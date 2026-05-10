package studio.one.platform.workspace.infrastructure.persistence.mybatis;

import org.apache.ibatis.annotations.Mapper;

@Mapper
interface WorkspaceConventionTestMapper {

    int selectConventionValue();
}
