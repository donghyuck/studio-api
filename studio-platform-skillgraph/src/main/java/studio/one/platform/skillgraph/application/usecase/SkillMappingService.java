package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import studio.one.platform.skillgraph.application.command.CourseSkillMappingCommand;
import studio.one.platform.skillgraph.application.command.NcsSkillMappingCommand;
import studio.one.platform.skillgraph.application.result.CourseSkillMappingView;
import studio.one.platform.skillgraph.application.result.NcsSkillMappingView;

public interface SkillMappingService {
    String SERVICE_NAME = "skillMappingService";

    NcsSkillMappingView saveNcsMapping(NcsSkillMappingCommand command);

    CourseSkillMappingView saveCourseMapping(CourseSkillMappingCommand command);

    List<NcsSkillMappingView> findNcsMappings(String ncsUnitId);

    List<CourseSkillMappingView> findCourseMappings(String courseId);
}
