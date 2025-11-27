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
 *      @file BaseEntity.java
 *      @date 2025
 *
 */

package studio.one.platform.domain.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;

import lombok.Getter;
import lombok.Setter;

/**
 * JPA 엔티티의 공통 속성을 정의하는 추상 클래스입니다.
 * <p>
 * 이 클래스를 상속받는 엔티티들은 데이터베이스 테이블과 매핑될 때,
 * 아래에 정의된 필드들을 자신의 컬럼으로 가지게 됩니다.
 * </p>
 *
 * <ul>
 * <li><b>createdAt</b>: 엔티티 생성 일시</li>
 * <li><b>updatedAt</b>: 엔티티 수정 일시</li>
 * <li><b>createdBy</b>: 엔티티 생성자</li>
 * <li><b>updatedBy</b>: 엔티티 수정자</li>
 * </ul>
 *
 * <p>
 * Lombok 라이브러리의 {@code @Getter}와 {@code @Setter} 어노테이션을 사용하여
 * 각 필드에 대한 getter와 setter 메서드를 자동으로 생성합니다.
 * </p>
 *
 * <p>
 * {@code @MappedSuperclass} 어노테이션은 이 클래스가 엔티티가 아니라,
 * 다른 엔티티 클래스들이 상속받아 사용할 속성들을 정의하는 슈퍼 클래스임을 나타냅니다.
 * </p>
 * 
 * @author donghyuck, son
 * @since 2025-08-08
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-08  donghyuck, son: 최초 생성.
 *          </pre>
 */

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "CREATED_BY")
    private String createdBy;

    @Column(name = "UPDATED_BY")
    private String updatedBy;

}
