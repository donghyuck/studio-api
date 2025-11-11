package studio.one.base.security.acl.domain.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Entity representing the ACL class table which stores domain identifiers.
 */
@Getter
@Setter
@ToString
@Entity
@Table(name = "acl_class")
public class AclClassEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class", nullable = false, length = 100)
    private String className;
}
