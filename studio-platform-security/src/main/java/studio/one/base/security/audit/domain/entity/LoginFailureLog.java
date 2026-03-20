package studio.one.base.security.audit.domain.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
 
@Entity
@Table(name = "tb_login_failure_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class LoginFailureLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 150)
  private String username;

  @JdbcTypeCode(SqlTypes.INET)
  @Column(name = "remote_ip", length = 64, columnDefinition = "inet") // ← Postgres inet
  private String remoteIp;

  @Column(name = "user_agent", length = 512)
  private String userAgent;

  @Column(name = "failure_type", length = 128)
  private String failureType;

  @Column(length = 1000)
  private String message;

  @CreationTimestamp // Hibernate가 insert 시각 자동 세팅
  @Column(name = "occurred_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
  private Instant occurredAt;

}
