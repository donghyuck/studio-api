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
 *      @file PageResult.java
 *      @date 2025
 *
 */

package studio.one.platform.storage.service;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 커서 기반 페이징을 처리하기 위한 응답 객체.
 * 
 * @author donghyuck, son
 * @since 2025-11-05
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-05  donghyuck, son: 최초 생성.
 *          </pre>
 */

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResult<T> {
  private List<T> items;
  private List<String> commonPrefixes;
  private String nextToken;
  private boolean truncated;
}
