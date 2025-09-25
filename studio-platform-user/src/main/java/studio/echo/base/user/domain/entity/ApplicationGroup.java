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
 *      @file ApplicationGroup.java
 *      @date 2025
 *
 */

package studio.echo.base.user.domain.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Singular;
import studio.echo.base.user.domain.model.Group;
import studio.echo.base.user.util.ApplicationJpaNames;

@Entity(name = ApplicationJpaNames.Group.ENTITY)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "TB_APPLICATION_GROUP")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationGroup implements Group {

	@Id // tell persistence provider 'id' is primary key
	@Column(name = "GROUP_ID", nullable = false)
	@GeneratedValue( // tell persistence provider that value of 'id' will be generated
			strategy = GenerationType.IDENTITY // use RDBMS unique id generator
	)
	Long groupId;

	@Column(name = "NAME", nullable = false, unique = true)
	String name;

	@Column(name = "DESCRIPTION")
	String description;

	
	@CreatedDate
	@Column(name = "CREATION_DATE", updatable = false)
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
	Instant creationDate;

	@LastModifiedDate
	@Column(name = "MODIFIED_DATE")
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
	Instant modifiedDate;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "TB_APPLICATION_GROUP_PROPERTY", joinColumns = {
			@JoinColumn(name = "GROUP_ID", referencedColumnName = "GROUP_ID") })
	@MapKeyColumn(name = "PROPERTY_NAME")
	@Column(name = "PROPERTY_VALUE")
	@Singular
	Map<String, String> properties;

	@OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private Set<ApplicationGroupRole> groupRoles = new HashSet<>();

	@OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private Set<ApplicationGroupMembership> memberships = new HashSet<>();

	@Transient @Default
	private Integer memberCount = 0;

	public ApplicationGroup(Long groupId, String name, String description, Long memberCount) {
        this.groupId = groupId;
        this.name = name;
        this.description = description;
        this.memberCount = memberCount == null ? 0 : memberCount.intValue();
    }

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
