package studio.one.platform.skillgraph.application.result;

import studio.one.platform.skillgraph.domain.model.SkillClusterMember;

public record SkillClusterMemberView(
        String clusterId,
        String skillId,
        String embeddingId,
        String projectionId,
        double membershipScore,
        double distanceToCentroid,
        boolean representative) {

    public static SkillClusterMemberView from(SkillClusterMember member) {
        return new SkillClusterMemberView(
                member.clusterId(),
                member.skillId(),
                member.embeddingId(),
                member.projectionId(),
                member.membershipScore(),
                member.distanceToCentroid(),
                member.representative());
    }
}
