package studio.one.platform.team.infrastructure.persistence.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.platform.team.domain.model.TeamJoinRequestRef;
import studio.one.platform.team.domain.model.TeamJoinRequestStatus;

@Entity
@Table(name = "TB_PLATFORM_TEAM_JOIN_REQUEST")
@Getter
@Setter
@NoArgsConstructor
public class TeamJoinRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REQUEST_ID", nullable = false)
    private Long requestId;

    @Column(name = "TEAM_ID", nullable = false)
    private Long teamId;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private TeamJoinRequestStatus status = TeamJoinRequestStatus.PENDING;

    @Column(name = "REQUESTED_AT", nullable = false)
    private Instant requestedAt;

    @Column(name = "RESOLVED_AT")
    private Instant resolvedAt;

    @Column(name = "RESOLVED_BY")
    private Long resolvedBy;

    public TeamJoinRequestRef toRef() {
        return new TeamJoinRequestRef(requestId, teamId, userId, status, requestedAt, resolvedAt, resolvedBy);
    }

    @PrePersist
    void onCreate() {
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
        if (status == null) {
            status = TeamJoinRequestStatus.PENDING;
        }
    }
}
