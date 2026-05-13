package studio.one.custom.user.domain.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;
import studio.one.base.user.domain.model.Status;
import studio.one.base.user.domain.model.User;

/**
 * Custom User 구현 예제. 기본 ApplicationUser 대신 사용할 때 참조용으로 제공한다.
 */
@Getter
@Setter
@Entity
@Table(name = "TB_CUSTOM_USER")
public class CustomUser implements User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "USERNAME", nullable = false, unique = true)
    private String username;

    @Column(name = "NAME")
    private String name;

    @Column(name = "FIRST_NAME")
    private String firstName;

    @Column(name = "LAST_NAME")
    private String lastName;

    @Column(name = "PASSWORD_HASH")
    @JsonIgnore
    private String password;

    @Column(name = "EMAIL", nullable = false)
    private String email;

    @Column(name = "NAME_VISIBLE", nullable = false)
    private boolean nameVisible;

    @Column(name = "EMAIL_VISIBLE", nullable = false)
    private boolean emailVisible;

    @Column(name = "USER_ENABLED", nullable = false)
    private boolean enabled = true;

    @Column(name = "USER_EXTERNAL", nullable = false)
    private boolean external;

    @Column(name = "STATUS")
    private Status status = Status.NONE;

    @Column(name = "FAILED_ATTEMPTS", nullable = false)
    private int failedAttempts;

    @Column(name = "LAST_FAILED_AT")
    private Instant lastFailedAt;

    @Column(name = "ACCOUNT_LOCKED_UNTIL")
    private Instant accountLockedUntil;

    @CreatedDate
    @Column(name = "CREATION_DATE", updatable = false)
    private Instant creationDate;

    @LastModifiedDate
    @Column(name = "MODIFIED_DATE")
    private Instant modifiedDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "TB_CUSTOM_USER_PROPERTY", joinColumns = @JoinColumn(name = "USER_ID"), uniqueConstraints = @UniqueConstraint(name = "PK_TB_CUSTOM_USER_PROPERTY", columnNames = {
            "USER_ID", "PROPERTY_NAME" }))
    @MapKeyColumn(name = "PROPERTY_NAME", length = 100)
    @Column(name = "PROPERTY_VALUE", length = 1024, nullable = false)
    private Map<String, String> properties = new HashMap<>();

    @Override
    public boolean isAnonymous() {
        return userId != null && userId == -1L;
    }

    @Override
    public boolean isAccountLockedNow(Instant now) {
        return accountLockedUntil != null && now.isBefore(accountLockedUntil);
    }
}
