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
 *      @file RefreshToken.java
 *      @date 2025
 *
 */

package studio.one.base.security.jwt.refresh.domain.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 *
 * @author  donghyuck, son
 * @since 2025-09-30
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-09-30  donghyuck, son: 최초 생성.
 * </pre>
 */

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(
  name = "TB_APPLICATIONI_REFRESH_TOKEN",
  indexes = {
    @Index(name = "IX_REFRESH_USER", columnList = "USER_ID"),
    @Index(name = "IX_REFRESH_EXPIRES", columnList = "EXPIRES_AT")
  },
  uniqueConstraints = {
    @UniqueConstraint(name = "UX_REFRESH_SELECTOR", columnNames = "SELECTOR")
  }
)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 토큰 조회용 public id (예: UUID). 유니크 인덱스 */
    @Column(name = "SELECTOR", nullable = false, length = 50)
    private String selector;

    /** verifier 의 BCrypt 해시(약 60자). */
    @Column(name = "VERIFIER_HASH", nullable = false, length = 100)
    private String verifierHash;

    @Column(name = "EXPIRES_AT", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(name = "REVOKED", nullable = false)
    private boolean revoked = false;

    @Builder.Default
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** 회전 시 새 토큰 id (선택) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REPLACED_BY_ID")
    private RefreshToken replacedBy;
}
