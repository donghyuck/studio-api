package studio.one.base.security.audit.domain.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
 

@TypeDef(name = "pg-inet", typeClass = PgInetUserType.class)
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

  @Type(type = "pg-inet")
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
