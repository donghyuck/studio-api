package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.NcsSkillMapping;

public record NcsSkillMappingView(String mappingId, String ncsUnitId, String skillId, double weight, Instant createdAt) {
    public static NcsSkillMappingView from(NcsSkillMapping mapping) {
        return new NcsSkillMappingView(mapping.mappingId(), mapping.ncsUnitId(), mapping.skillId(), mapping.weight(), mapping.createdAt());
    }
}
