package studio.one.platform.skillgraph.application.service;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.CourseSkillMappingCommand;
import studio.one.platform.skillgraph.application.command.NcsSkillMappingCommand;
import studio.one.platform.skillgraph.application.result.CourseSkillMappingView;
import studio.one.platform.skillgraph.application.result.NcsSkillMappingView;
import studio.one.platform.skillgraph.application.usecase.SkillMappingService;
import studio.one.platform.skillgraph.domain.model.CourseSkillMapping;
import studio.one.platform.skillgraph.domain.model.NcsSkillMapping;
import studio.one.platform.skillgraph.domain.port.SkillMappingStore;

@RequiredArgsConstructor
public class DefaultSkillMappingService implements SkillMappingService {
    private final SkillMappingStore store;

    @Override
    public NcsSkillMappingView saveNcsMapping(NcsSkillMappingCommand command) {
        String id = idOrNew(command.mappingId(), "ncsmap_");
        return NcsSkillMappingView.from(store.saveNcsMapping(new NcsSkillMapping(id, command.ncsUnitId(), command.skillId(), command.weight(), null)));
    }

    @Override
    public CourseSkillMappingView saveCourseMapping(CourseSkillMappingCommand command) {
        String id = idOrNew(command.mappingId(), "crsmap_");
        return CourseSkillMappingView.from(store.saveCourseMapping(new CourseSkillMapping(id, command.courseId(), command.skillId(), command.weight(), null)));
    }

    @Override
    public List<NcsSkillMappingView> findNcsMappings(String ncsUnitId) {
        return store.findNcsMappings(ncsUnitId).stream().map(NcsSkillMappingView::from).toList();
    }

    @Override
    public List<CourseSkillMappingView> findCourseMappings(String courseId) {
        return store.findCourseMappings(courseId).stream().map(CourseSkillMappingView::from).toList();
    }

    private String idOrNew(String value, String prefix) {
        return value == null || value.isBlank() ? prefix + UUID.randomUUID() : value.trim();
    }
}
