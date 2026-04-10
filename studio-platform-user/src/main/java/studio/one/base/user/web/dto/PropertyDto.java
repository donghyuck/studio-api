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
 *      @file PropertyDto.java
 *      @date 2026
 *
 */

package studio.one.base.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

/**
 * DTO for a single key-value property entry.
 * Used for user/group property management endpoints.
 *
 * @author donghyuck, son
 * @since 2026-04-10
 * @version 1.0
 *
 * <pre>
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2026-04-10  donghyuck, son: 최초 생성.
 * </pre>
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertyDto {

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[A-Za-z0-9_.-]{1,100}", message = "Key must contain only alphanumeric characters, underscores, dots, or hyphens")
    String key;

    @Size(max = 1024)
    String value;

}
