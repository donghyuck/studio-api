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
 *      @file ApiResponse.java
 *      @date 2025
 *
 */


package studio.echo.platform.web.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;
/**
 * ApiResponse is a generic class that represents the structure of an API response.
 * It includes fields for success status, data, an optional message, and optional metadata.
 * It is designed to be used in RESTful APIs to standardize the response format.
 * 
 * @author  donghyuck, son
 * @since 2025-08-22
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-22  donghyuck, son: 최초 생성.
 * </pre>
 */



@Getter
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    @Builder.Default
    private final boolean success = true;
    private final T data;
    private final String message; // optional
    private final Map<String, Object> meta; // optional (paging, etc.)

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().data(data).build();
    }
    
    public static  <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder().message(message).data(data).build();
    }

    public static ApiResponse<Void> ok() {
        return ApiResponse.<Void>builder().build();
    }


}
    