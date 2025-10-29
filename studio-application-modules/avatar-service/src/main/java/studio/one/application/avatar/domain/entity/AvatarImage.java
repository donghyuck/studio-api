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
 *      @file ApplicationAvatarImage.java
 *      @date 2025
 *
 */

package studio.one.application.avatar.domain.entity;

import java.time.OffsetDateTime;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;

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
@Table(name = "TB_APPLICATION_AVATAR_IMAGE",
       indexes = {
         @Index(name = "TB_APPLICATION_AVATAR_IMAGE_IDX1", columnList = "USER_ID")
       })
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Builder
public class AvatarImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // BIGSERIAL과 매칭
    @Column(name = "AVATAR_IMAGE_ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "PRIMARY_IMAGE", nullable = false)
    @Builder.Default
    private boolean primaryImage = true;

    @Column(name = "FILE_NAME", nullable = false, length = 255)
    private String fileName;

    @Column(name = "FILE_SIZE", nullable = false)
    private Long fileSize;

    @Column(name = "CONTENT_TYPE", nullable = false, length = 50)
    private String contentType;

    @CreationTimestamp
    @Column(name = "CREATION_DATE", columnDefinition = "timestamptz")
    private OffsetDateTime creationDate;

    @UpdateTimestamp
    @Column(name = "MODIFIED_DATE", columnDefinition = "timestamptz")
    private OffsetDateTime modifiedDate;

    /** 데이터 테이블과 1:1 (공유 PK) — data 측에서 @MapsId 로 연결 */
    @OneToOne(mappedBy = "avatarImage", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference                        
    @ToString.Exclude @EqualsAndHashCode.Exclude 
    private AvatarImageData data;

    /** 편의 메서드 */
    public void attachData(AvatarImageData data) {
        this.data = data;
        if (data != null) data.setAvatarImage(this);
    }
}
