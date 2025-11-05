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
 *      @file GroupEndpoint.java
 *      @date 2025
 *
 */

package studio.one.base.user.web.controller;

import static org.springframework.http.ResponseEntity.ok;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.domain.model.Group;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.service.ApplicationGroupService;
import studio.one.base.user.service.BatchResult;
import studio.one.base.user.web.dto.GroupDto;
import studio.one.base.user.web.dto.RoleDto;
import studio.one.base.user.web.dto.UserDto;
import studio.one.base.user.web.mapper.ApplicationGroupMapper;
import studio.one.base.user.web.mapper.ApplicationRoleMapper;
import studio.one.base.user.web.mapper.ApplicationUserMapper;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

/**
 *
 * @author donghyuck, son
 * @since 2025-08-27
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-27  donghyuck, son: 최초 생성.
 *          </pre>
 */

@RestController
@RequestMapping("${" + PropertyKeys.Features.User.Web.BASE_PATH + ":/api/mgmt}/groups")
@RequiredArgsConstructor
@Slf4j
public class GroupController {

    private final ApplicationGroupService<Group, Role, User> groupService;
    private final ApplicationGroupMapper groupMapper;
    private final ApplicationUserMapper userMapper;
    private final ApplicationRoleMapper roleMapper;

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('group','read')")
    public ResponseEntity<ApiResponse<Page<GroupDto>>> list(
            @PageableDefault(size = 15, sort = "groupId", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Group> page = groupService.getGroupsWithMemberCount(pageable);
        Page<GroupDto> dtoPage = page.map(groupMapper::toDto);
        return ok(ApiResponse.ok(dtoPage));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('group','read')")
    public ResponseEntity<ApiResponse<GroupDto>> get(@PathVariable Long id) {
        Group group = groupService.getById(id);
        return ok(ApiResponse.ok(groupMapper.toDto(group)));
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('group','write')")
    public ResponseEntity<ApiResponse<GroupDto>> create(@Valid @RequestBody GroupDto req) {
        Group group = groupMapper.toEntity(req);
        Group saved = groupService.createGroup(group);
        // Location 헤더 (선택)
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(String.format("/groups/%s", saved.getGroupId())));
        return new ResponseEntity<>(ApiResponse.ok(groupMapper.toDto(saved)), headers, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('group','write')")
    public ResponseEntity<ApiResponse<GroupDto>> update(@PathVariable Long id, @Valid @RequestBody GroupDto dto) {
        Group updated = groupService.updateGroup(id, g -> groupMapper.updateEntityFromDto(dto, g));
        return ok(ApiResponse.ok(groupMapper.toDto(updated)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('group','write')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        groupService.deleteGroup(id);
        return ok(ApiResponse.ok());
    }

    // Roles --------------------------------------
    @GetMapping("/{id}/roles")
    @PreAuthorize("@endpointAuthz.can('group','read')")
    public ResponseEntity<ApiResponse<List<RoleDto>>> roles(@PathVariable Long id) {
        List<Role> list = groupService.getRoles(id);
        return ok(ApiResponse.ok(roleMapper.toDtos(list)));
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("@endpointAuthz.can('group','write')")
    public ResponseEntity<ApiResponse<Void>> updateGroupRoles(@PathVariable Long id, @RequestBody List<RoleDto> roles,
            @AuthenticationPrincipal UserDetails actor) {
        if (actor == null) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        } 
        List<Long> desired = Optional.ofNullable(roles).orElseGet(Collections::emptyList)
        .stream()
        .map(RoleDto::getRoleId)
        .filter(Objects::nonNull)
        .distinct().toList(); 
        BatchResult result =  groupService.updateGroupRolesBulk(id, desired, actor.getUsername());
        log.debug("batch : {}" , result);
        return ok(ApiResponse.ok());
    }

    // Membership --------------------------------------
    @GetMapping("/{id}/members")
    @PreAuthorize("@endpointAuthz.can('group','read')")
    public ResponseEntity<ApiResponse<Page<UserDto>>> members(
            @PathVariable Long id,
            @PageableDefault(size = 15, direction = Sort.Direction.DESC) Pageable pageable) {
        Group group = groupService.getById(id);
        Page<User> page = groupService.getMembers(group.getGroupId(), pageable);
        Page<UserDto> dtoPage = page.map(userMapper::toDto);
        return ok(ApiResponse.ok(dtoPage));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("@endpointAuthz.can('group','write')")
    public ResponseEntity<ApiResponse<Integer>> addMemberships(
            @PathVariable Long id,
            @RequestBody List<Long> userList,
            @AuthenticationPrincipal UserDetails principal) {
        int result = groupService.addMembersBulk(id, userList, principal.getUsername(), java.time.OffsetDateTime.now());
        return ok(ApiResponse.ok(result));
    }

    @DeleteMapping("/{id}/members")
    @PreAuthorize("@endpointAuthz.can('group','write')")
    public ResponseEntity<ApiResponse<Integer>> removeaddMemberships(@PathVariable Long id,
            @RequestBody List<Long> userList) {
        int result = groupService.removeMembers(id, userList);
        return ok(ApiResponse.ok(result));
    }

}