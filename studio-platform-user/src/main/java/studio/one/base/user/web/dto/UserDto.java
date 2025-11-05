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
 *      @file UserDto.java
 *      @date 2025
 *
 */

package studio.one.base.user.web.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Builder;
import lombok.Value;
import studio.one.base.user.domain.model.Status;
import studio.one.base.user.domain.model.json.JsonStatusDeserializer;
import studio.one.base.user.domain.model.json.JsonStatusSerializer; 

/**
 *
 * @author  donghyuck, son
 * @since 2025-10-14
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-10-14  donghyuck, son: 최초 생성.
 * </pre>
 */

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDto {

	Long userId;
	String username;
	String name;
	String email;
  	boolean emailVisible;
	boolean nameVisible;
	boolean enabled;
	String lastName;
	String firstName;

	@JsonSerialize(using = JsonStatusSerializer.class)
    @JsonDeserialize(using = JsonStatusDeserializer.class) // 이미 있으니 함께 명시
	Status status;

	OffsetDateTime creationDate;
	OffsetDateTime modifiedDate;
	Map<String, String> properties;
	
}
