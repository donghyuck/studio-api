/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file ApplicationUser.java
 *      @date 2025
 *
 */

package studio.one.base.user.domain.entity;

import java.time.Instant;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import studio.one.base.user.constant.JpaEntityNames;
import studio.one.base.user.domain.model.Status;
import studio.one.base.user.domain.model.User;

@Builder
@Entity(name = JpaEntityNames.User.ENTITY)
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
@Getter
@Setter
@Table(name = "TB_APPLICATION_USER")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class ApplicationUser implements User {

	@Id // tell persistence provider 'id' is primary key
	@EqualsAndHashCode.Include
	@ToString.Include
	@Column(name = "USER_ID", nullable = false)
	@GeneratedValue( // tell persistence provider that value of 'id' will be generated
			strategy = GenerationType.IDENTITY // use RDBMS unique id generator
	)
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

	@Column(name = "NAME_VISIBLE", nullable = false, length = 1)
	private boolean nameVisible;

	@Column(name = "EMAIL", nullable = false)
	private String email;

	@Column(name = "EMAIL_VISIBLE", nullable = false, length = 1)
	private boolean emailVisible;

	@Column(name = "USER_ENABLED", nullable = false, length = 1)
	private boolean enabled;

	@Column(name = "USER_EXTERNAL", nullable = false, length = 1)
	private boolean external;

	@Enumerated(EnumType.ORDINAL)
	@Column(name = "STATUS")
	private Status status;

	@Column(name = "FAILED_ATTEMPTS", nullable = false)
	private int failedAttempts;

	@Column(name = "LAST_FAILED_AT")
	private Instant lastFailedAt;

	@Column(name = "ACCOUNT_LOCKED_UNTIL")
	private Instant accountLockedUntil;

	@CreatedDate
	@Column(name = "CREATION_DATE", updatable = false)
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
	private Instant creationDate;

	@LastModifiedDate
	@Column(name = "MODIFIED_DATE")
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
	private Instant modifiedDate;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "TB_APPLICATION_USER_PROPERTY", joinColumns = {
			@JoinColumn(name = "USER_ID", referencedColumnName = "USER_ID") }, uniqueConstraints = @UniqueConstraint(name = "PK_TB_APPLICATION_USER_PROPERTY", columnNames = {
					"USER_ID", "PROPERTY_NAME" }))
	@MapKeyColumn(name = "PROPERTY_NAME", length = 100)
	@Column(name = "PROPERTY_VALUE", length = 1024, nullable = false)
	private Map<String, String> properties;

	public boolean isAnonymous() {
		return this.userId == -1L;
	}

	@jakarta.persistence.PrePersist
	void onCreate() {
		if (creationDate == null)
			creationDate = java.time.Instant.now();
		if (modifiedDate == null)
			modifiedDate = creationDate;
		if (failedAttempts < 0)
			failedAttempts = 0;
		if (status == null)
			status = Status.NONE;
	}

	@jakarta.persistence.PreUpdate
	void onUpdate() {
		if (modifiedDate == null)
			modifiedDate = java.time.Instant.now();
	}

	@JsonIgnore
	public boolean isAccountLockedNow(Instant now) {
		return accountLockedUntil != null && now.isBefore(accountLockedUntil);
	}

}
