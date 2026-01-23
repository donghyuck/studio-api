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
import java.util.Set;
import java.util.stream.Collectors;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.domain.model.Group;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.service.ApplicationGroupService;
import studio.one.base.user.service.BatchResult;
import studio.one.base.user.web.dto.GroupDto;
import studio.one.base.user.web.dto.RoleDto;
import studio.one.base.user.web.mapper.ApplicationGroupMapper;
import studio.one.base.user.web.mapper.ApplicationRoleMapper;
import studio.one.base.user.web.util.RequestParamUtils;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.UserDto;
import studio.one.platform.identity.UserRef;
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
public class GroupMgmtController {

    private final ApplicationGroupService<Group, Role> groupService;
    private final ApplicationGroupMapper groupMapper;
    private final ApplicationRoleMapper roleMapper;
    private final org.springframework.beans.factory.ObjectProvider<IdentityService> identityServiceProvider;
    private static final List<String> ALLOWED_FIELDS = List.of(
            "groupId",
            "name",
            "description",
            "creationDate",
            "modifiedDate",
            "properties", 
            "roleCount", 
            "memberCount");
    private static final String ALLOWED_FIELDS_HEADER = RequestParamUtils.allowedFieldsHeader(ALLOWED_FIELDS);
    private static final Set<String> ALLOWED_FIELDS_LOWER = ALLOWED_FIELDS.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    private static final Set<String> DEFAULT_FIELDS = ALLOWED_FIELDS_LOWER;

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:group','read')")
    public ResponseEntity<ApiResponse<Page<GroupDto>>> list(
            @RequestParam(value = "q", required = false) Optional<String> q,
            @RequestParam(value = "fields", required = false) Optional<String> fields,
            @PageableDefault(size = 15, sort = "groupId", direction = Sort.Direction.DESC) Pageable pageable) {
        String keyword = RequestParamUtils.normalizeQuery(q).orElse(null);
        Page<Group> page = groupService.getGroupsWithMemberCount(keyword, pageable);
        Page<GroupDto> dtoPage = page.map(groupMapper::toDto);
        Set<String> selected = parseFields(fields.orElse(null));
        if (!selected.isEmpty()) {
            dtoPage = dtoPage.map(dto -> selectFields(dto, selected));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Fields-Allowed", ALLOWED_FIELDS_HEADER);
        return ResponseEntity.ok().headers(headers).body(ApiResponse.ok(dtoPage));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('features:group','read')")
    public ResponseEntity<ApiResponse<GroupDto>> get(@PathVariable Long id) {
        Group group = groupService.getById(id);
        return ok(ApiResponse.ok(groupMapper.toDto(group)));
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:group','write')")
    public ResponseEntity<ApiResponse<GroupDto>> create(@Valid @RequestBody GroupDto req) {
        Group group = groupMapper.toEntity(req);
        Group saved = groupService.createGroup(group);
        // Location 헤더 (선택)
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(String.format("/groups/%s", saved.getGroupId())));
        return new ResponseEntity<>(ApiResponse.ok(groupMapper.toDto(saved)), headers, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('features:group','write')")
    public ResponseEntity<ApiResponse<GroupDto>> update(@PathVariable Long id, @Valid @RequestBody GroupDto dto) {
        Group updated = groupService.updateGroup(id, g -> groupMapper.updateEntityFromDto(dto, g));
        return ok(ApiResponse.ok(groupMapper.toDto(updated)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('features:group','write')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        groupService.deleteGroup(id);
        return ok(ApiResponse.ok());
    }

    // Roles --------------------------------------
    @GetMapping("/{id}/roles")
    @PreAuthorize("@endpointAuthz.can('features:group','read')")
    public ResponseEntity<ApiResponse<List<RoleDto>>> roles(@PathVariable Long id) {
        List<Role> list = groupService.getRoles(id);
        return ok(ApiResponse.ok(roleMapper.toDtos(list)));
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("@endpointAuthz.can('features:group','write')")
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
        BatchResult result = groupService.updateGroupRolesBulk(id, desired, actor.getUsername());
        log.debug("batch : {}", result);
        return ok(ApiResponse.ok());
    }

    // Membership --------------------------------------
    @GetMapping("/{id}/members")
    @PreAuthorize("@endpointAuthz.can('features:group','read')")
    public ResponseEntity<ApiResponse<Page<UserDto>>> members(
            @PathVariable Long id,
            @PageableDefault(size = 15, direction = Sort.Direction.DESC) Pageable pageable) {
        Group group = groupService.getById(id);
        Page<Long> page = groupService.getMembers(group.getGroupId(), pageable);
        Page<UserDto> dtoPage = page.map(this::toUserDto);
        return ok(ApiResponse.ok(dtoPage));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("@endpointAuthz.can('features:group','write')")
    public ResponseEntity<ApiResponse<Integer>> addMemberships(
            @PathVariable Long id,
            @RequestBody List<Long> userList,
            @AuthenticationPrincipal UserDetails principal) {
        int result = groupService.addMembersBulk(id, userList, principal.getUsername(), java.time.OffsetDateTime.now());
        return ok(ApiResponse.ok(result));
    }

    @DeleteMapping("/{id}/members")
    @PreAuthorize("@endpointAuthz.can('features:group','write')")
    public ResponseEntity<ApiResponse<Integer>> removeaddMemberships(@PathVariable Long id,
            @RequestBody List<Long> userList) {
        int result = groupService.removeMembers(id, userList);
        return ok(ApiResponse.ok(result));
    }

    private UserDto toUserDto(Long userId) {
        if (userId == null) {
            return null;
        }
        IdentityService identityService = identityServiceProvider.getIfAvailable();
        if (identityService == null) {
            return new UserDto(userId, null);
        }
        return identityService.findById(userId)
                .map(this::toUserDto)
                .orElseGet(() -> new UserDto(userId, null));
    }

    private UserDto toUserDto(UserRef userRef) {
        return new UserDto(userRef.userId(), userRef.username());
    }

    private static Set<String> parseFields(String raw) {
        return RequestParamUtils.parseFields(raw, ALLOWED_FIELDS_LOWER, DEFAULT_FIELDS);
    }

    private static GroupDto selectFields(GroupDto dto, Set<String> fields) {
        return GroupDto.builder()
                .groupId(fields.contains("groupid") ? dto.getGroupId() : null)
                .name(fields.contains("name") ? dto.getName() : null)
                .description(fields.contains("description") ? dto.getDescription() : null)
                .properties(fields.contains("properties") ? dto.getProperties() : null)
                .creationDate(fields.contains("creationdate") ? dto.getCreationDate() : null)
                .modifiedDate(fields.contains("modifieddate") ? dto.getModifiedDate() : null)
                .roleCount(fields.contains("rolecount") ? dto.getRoleCount() : null)
                .memberCount(fields.contains("membercount") ? dto.getMemberCount() : null)
                .build();
    }

}
