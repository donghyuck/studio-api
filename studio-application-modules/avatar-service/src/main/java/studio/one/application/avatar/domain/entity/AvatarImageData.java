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
 *      @file ApplicationAvatarImageData.java
 *      @date 2025
 *
 */

package studio.one.application.avatar.domain.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 *
 * @author  donghyuck, son
 * @since 2025-10-20
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-10-20  donghyuck, son: 최초 생성.
 * </pre>
 */

@Entity
@Table(name = "TB_APPLICATION_AVATAR_IMAGE_DATA")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Builder
public class AvatarImageData {

    /** 공유 PK — 메타의 AVATAR_IMAGE_ID 를 그대로 사용 */
    @Id
    @Column(name = "AVATAR_IMAGE_ID")
    private Long id;

    @Type(type = "org.hibernate.type.BinaryType")
    @Column(name = "AVATAR_IMAGE_DATA", columnDefinition = "bytea", nullable = true) 
    @JsonIgnore   
    private byte[] data;

    @OneToOne
    @MapsId               // 공유 PK 핵심
    @JoinColumn(name = "AVATAR_IMAGE_ID")
    @JsonBackReference                         
    @ToString.Exclude @EqualsAndHashCode.Exclude
    private AvatarImage avatarImage;
}
