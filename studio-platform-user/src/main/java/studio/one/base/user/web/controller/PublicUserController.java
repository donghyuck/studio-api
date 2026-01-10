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
 *      @file PublicUserController.java
 *      @date 2025
 *
 */

package studio.one.base.user.web.controller;

import static org.springframework.http.ResponseEntity.ok;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.base.user.web.dto.UserPublicDto;
import studio.one.base.user.web.mapper.ApplicationUserMapper;
import studio.one.platform.web.dto.ApiResponse;
import studio.one.platform.constant.PropertyKeys;
/**
 *
 * Public user endpoints.
 *
 * @author donghyuck, son
 * @since 2025-11-18
 * @version 1.0
 *
 *          <pre>
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-18  donghyuck, son: 최초 생성.
 *          </pre>
 */

@RestController 
@RequestMapping("${" + PropertyKeys.Features.User.PREFIX + ".public-path:/api/users}")
@RequiredArgsConstructor 
@Slf4j
public class PublicUserController {

    private final ApplicationUserService<User, Role> userService;
    private final ApplicationUserMapper userMapper;
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserPublicDto>> get(@PathVariable Long id) {
        var user = userService.get(id);
        return ok(ApiResponse.ok(userMapper.toPublicDto(user)));
    }

}
