package studio.one.platform.skillgraph.domain.port;

import java.util.List;

import studio.one.platform.skillgraph.domain.model.CourseSkillMapping;
import studio.one.platform.skillgraph.domain.model.NcsSkillMapping;

public interface SkillMappingStore {

    String SERVICE_NAME = "skillMappingStore";

    NcsSkillMapping saveNcsMapping(NcsSkillMapping mapping);

    CourseSkillMapping saveCourseMapping(CourseSkillMapping mapping);

    List<NcsSkillMapping> findNcsMappings(String ncsUnitId);

    List<CourseSkillMapping> findCourseMappings(String courseId);

    List<CourseSkillMapping> findCoursesBySkillIds(List<String> skillIds);
}
