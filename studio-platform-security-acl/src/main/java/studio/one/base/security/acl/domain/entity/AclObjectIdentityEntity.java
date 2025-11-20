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
 *      @file AclObjectIdentityEntity.java
 *      @date 2025
 *
 */

package studio.one.base.security.acl.domain.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Entity representing object identities within the ACL system.
 */
@Getter
@Setter
@ToString(exclude = { "parent", "entries" })
@Entity
@Table(name = "acl_object_identity")
public class AclObjectIdentityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "object_id_class", nullable = false)
    private AclClassEntity aclClass;

    @Column(name = "object_id_identity", nullable = false, length = 36)
    private String objectIdIdentity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_object")
    private AclObjectIdentityEntity parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_sid")
    private AclSidEntity ownerSid;

    @Column(name = "entries_inheriting", nullable = false)
    private boolean entriesInheriting;

    @OneToMany(mappedBy = "aclObjectIdentity", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<AclEntryEntity> entries = new LinkedHashSet<>();
}
