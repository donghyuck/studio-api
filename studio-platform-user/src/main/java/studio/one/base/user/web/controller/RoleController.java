package studio.one.base.user.web.controller;

import static org.springframework.http.ResponseEntity.ok;

import java.net.URI;
import java.util.List;

import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import studio.one.base.user.domain.entity.ApplicationRole;
import studio.one.base.user.domain.model.Group;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.service.ApplicationRoleService;
import studio.one.base.user.service.BatchResult;
import studio.one.base.user.web.dto.GroupDto;
import studio.one.base.user.web.dto.RoleDto;
import studio.one.base.user.web.dto.UserDto;
import studio.one.base.user.web.mapper.ApplicationGroupMapper;
import studio.one.base.user.web.mapper.ApplicationRoleMapper;
import studio.one.base.user.web.mapper.ApplicationUserMapper;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.annotation.Message;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.User.Web.BASE_PATH + ":/api/mgmt}/roles")
@RequiredArgsConstructor
@Slf4j
public class RoleController {

    private final ApplicationRoleService<Role, Group, User> roleService;
    private final ApplicationRoleMapper mapper;
    private final ApplicationGroupMapper groupMapper;
    private final ApplicationUserMapper userMapper;

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('role','read')")
    public ResponseEntity<ApiResponse<Page<RoleDto>>> list(
            @PageableDefault(size = 15, sort = "roleId", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Role> page = roleService.getRoles(pageable);
        Page<RoleDto> dtoPage = page.map(mapper::toDto);
        return ok(ApiResponse.ok(dtoPage));
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('role','write')")
    @Message(value = "success.role.created.named", args = { "#req.name" })
    public ResponseEntity<ApiResponse<RoleDto>> create(@Valid @RequestBody RoleDto req) {
        Role role = mapper.toEntity(req);
        Role saved = roleService.createRole(role);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(String.format("/roles/%s", saved.getRoleId())));
        return new ResponseEntity<>(ApiResponse.ok(mapper.toDto(saved)), headers, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('role','read')")
    public ResponseEntity<ApiResponse<RoleDto>> get(@PathVariable Long id) {
        Role role = roleService.getRoleById(id);
        return ok(ApiResponse.ok(mapper.toDto(role)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('role','write')")
    @Message(value = "success.role.updated.named", args = { "#dto.name" })
    public ResponseEntity<ApiResponse<RoleDto>> update(@PathVariable Long id, @Valid @RequestBody RoleDto dto) {
        Role updated = roleService.updateRole(id, r -> mapper.updateEntityFromDto(dto, (ApplicationRole) r));
        return ok(ApiResponse.ok(mapper.toDto(updated)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('group','write')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ok(ApiResponse.ok());
    }

    @GetMapping("/{roleId}/groups")
    @PreAuthorize("@endpointAuthz.can('role','read')")
    public ResponseEntity<ApiResponse<Page<GroupDto>>> findGroupsGrantedRole(
            @PathVariable Long roleId,
            @RequestParam(name = "q", required = false) String q,
            @PageableDefault(size = 15, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        var page = roleService.findGroupsGrantedRole(roleId, q, pageable).map(groupMapper::toDto);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @DeleteMapping("/{roleId}/groups")
    @PreAuthorize("@endpointAuthz.can('role','write')")
    public ResponseEntity<ApiResponse<BatchResult>> revokeRoleFromgroups(
            @PathVariable Long roleId,
            @RequestBody List<Long> groups) {
        var result = roleService.revokeRoleFromGroups(groups, roleId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{roleId}/users")
    @PreAuthorize("@endpointAuthz.can('role','read')")
    public ResponseEntity<ApiResponse<Page<UserDto>>> findUsersGrantedRole(
            @PathVariable Long roleId,
            @RequestParam(name = "scope", defaultValue = "direct") String scope,
            @RequestParam(name = "q", required = false) String q,
            @PageableDefault(size = 15, sort = "username", direction = Sort.Direction.ASC) Pageable pageable) {
        var page = roleService.findUsersGrantedRole(roleId, scope, q, pageable).map(userMapper::toDto);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @DeleteMapping("/{roleId}/users")
    @PreAuthorize("@endpointAuthz.can('role','write')")
    public ResponseEntity<ApiResponse<BatchResult>> revokeRoleFromUsers(
            @PathVariable Long roleId,
            @RequestBody List<Long> users) {
        var result = roleService.revokeRoleFromUsers(users, roleId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PutMapping("/{roleId}/users")
    @PreAuthorize("@endpointAuthz.can('role','write')")
    public ResponseEntity<ApiResponse<BatchResult>> assignRoleFromUsers(
            @PathVariable Long roleId,
            @RequestBody List<Long> users,
            @AuthenticationPrincipal UserDetails principal) { 
        var result = roleService.assignRoleToUsers(users, roleId, principal.getUsername(), java.time.OffsetDateTime.now());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

}
