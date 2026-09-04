package studio.one.platform.team.domain.model;

public record TeamJoinResult(
        TeamJoinOutcome outcome,
        TeamMemberRef member,
        TeamJoinRequestRef request) {
}
