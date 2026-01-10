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
 *      @file ApplicationGroupDto.java
 *      @date 2025
 *
 */


package studio.one.base.user.web.dto;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;
/**
 *
 * @author  donghyuck, son
 * @since 2025-09-08
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-09-08  donghyuck, son: 최초 생성.
 * </pre>
 */


@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupDto {

    Long groupId;

    @NotBlank
    @Size(max = 255)
    String name;

    @Size(max = 1000)
    String description;

    @Builder.Default
    Map<String, String> properties = Collections.emptyMap();

    OffsetDateTime creationDate;

    OffsetDateTime modifiedDate;

    @Builder.Default
    Integer roleCount = 0;

    Long memberCount ; 

}
