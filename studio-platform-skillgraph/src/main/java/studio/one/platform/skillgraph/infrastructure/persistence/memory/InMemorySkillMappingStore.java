package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import studio.one.platform.skillgraph.domain.model.CourseSkillMapping;
import studio.one.platform.skillgraph.domain.model.NcsSkillMapping;
import studio.one.platform.skillgraph.domain.port.SkillMappingStore;

public class InMemorySkillMappingStore implements SkillMappingStore {
    private final Map<String, NcsSkillMapping> ncsMappings = new ConcurrentHashMap<>();
    private final Map<String, CourseSkillMapping> courseMappings = new ConcurrentHashMap<>();

    @Override
    public NcsSkillMapping saveNcsMapping(NcsSkillMapping mapping) {
        ncsMappings.put(mapping.mappingId(), mapping);
        return mapping;
    }

    @Override
    public CourseSkillMapping saveCourseMapping(CourseSkillMapping mapping) {
        courseMappings.put(mapping.mappingId(), mapping);
        return mapping;
    }

    @Override
    public List<NcsSkillMapping> findNcsMappings(String ncsUnitId) {
        return ncsMappings.values().stream()
                .filter(mapping -> ncsUnitId == null || ncsUnitId.isBlank() || ncsUnitId.equals(mapping.ncsUnitId()))
                .sorted(Comparator.comparing(NcsSkillMapping::createdAt).reversed())
                .toList();
    }

    @Override
    public List<CourseSkillMapping> findCourseMappings(String courseId) {
        return courseMappings.values().stream()
                .filter(mapping -> courseId == null || courseId.isBlank() || courseId.equals(mapping.courseId()))
                .sorted(Comparator.comparing(CourseSkillMapping::createdAt).reversed())
                .toList();
    }

    @Override
    public List<CourseSkillMapping> findCoursesBySkillIds(List<String> skillIds) {
        return courseMappings.values().stream()
                .filter(mapping -> skillIds != null && skillIds.contains(mapping.skillId()))
                .toList();
    }
}
