package studio.one.platform.team.infrastructure.persistence.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "TB_PLATFORM_TEAM_MIGRATION_LOCK")
@Getter
@Setter
@NoArgsConstructor
public class TeamMigrationLockEntity {
    @Id
    @Column(name = "LOCK_KEY", nullable = false, length = 128)
    private String lockKey;

    @Column(name = "RUN_ID", nullable = false, length = 36)
    private String runId;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
