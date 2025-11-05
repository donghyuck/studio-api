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
 *      @file ApplicationRole.java
 *      @date 2025
 *
 */

package studio.one.base.user.domain.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.util.ApplicationJpaNames;

@Entity(name = ApplicationJpaNames.Role.ENTITY)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "TB_APPLICATION_ROLE")
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationRole implements Role {

	@Id @EqualsAndHashCode.Include @ToString.Include
	@Column(name = "ROLE_ID", nullable = false)
	@GeneratedValue( // tell persistence provider that value of 'id' will be generated
			strategy = GenerationType.IDENTITY // use RDBMS unique id generator
	)
	private Long roleId;

	@Column(name = "NAME", nullable = false, unique = true)
	private String name;

	@Column(name = "DESCRIPTION", nullable = false)
	private String description;

	@CreatedDate
	@Column(name = "CREATION_DATE", updatable = false)
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
	private Instant creationDate;

	@LastModifiedDate
	@Column(name = "MODIFIED_DATE")
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
	private Instant modifiedDate;

	@OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<ApplicationGroupRole> groupRoles = new HashSet<>();

	@OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonIgnore
	private Set<ApplicationUserRole> userRoles = new HashSet<>();

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (creationDate == null)
			creationDate = now;
		if (modifiedDate == null)
			modifiedDate = now;
	}

	@PreUpdate
	void onUpdate() {
		if (modifiedDate == null)
			modifiedDate = Instant.now();
	}
}