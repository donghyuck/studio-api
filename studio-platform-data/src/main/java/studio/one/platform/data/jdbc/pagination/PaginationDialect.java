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
 *      @file PaginationDialect.java
 *      @date 2025
 *
 */

package studio.one.platform.data.jdbc.pagination;

/**
 * DB별 페이지네이션 SQL을 생성하는 Dialect 인터페이스.
 *
 * @author  donghyuck, son
 * @since 2025-12-03
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-12-03  donghyuck, son: 최초 생성.
 * </pre>
 */

public interface PaginationDialect {

    /**
     * @param sql    원본 SQL (ORDER BY 포함)
     * @param offset 시작 인덱스(0 기반)
     * @param limit  가져올 행 수
     * @return DB에 맞게 변환된 페이징 SQL
     */
    String applyPagination(String sql, int offset, int limit);

    /**
     * DB 버전에 따라 페이징 문법이 안 맞는 경우가 있을 수 있으므로
     * 필요하면 false로 두고 상위에서 fallback 가능.
     */
    default boolean supportsPagination() {
        return true;
    }
}